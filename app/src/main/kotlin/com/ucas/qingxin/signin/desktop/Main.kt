package com.ucas.qingxin.signin.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ucas.qingxin.signin.data.Course
import com.ucas.qingxin.signin.data.QrSnapshot
import com.ucas.qingxin.signin.data.SchoolSession
import com.ucas.qingxin.signin.data.SignOutcome
import com.ucas.qingxin.signin.network.ApiException
import com.ucas.qingxin.signin.network.QingxinApiService
import com.ucas.qingxin.signin.qr.QrTimelineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val UcasBlue = Color(0xFF174A7E)
private val UcasBlueSoft = Color(0xFFE7F0F8)
private val SuccessGreen = Color(0xFF1B6B3A)
private val ShanghaiZone = ZoneId.of("Asia/Shanghai")
private val CourseDateFormatter = DateTimeFormatter.BASIC_ISO_DATE

fun main() = application {
    val controller = DesktopController()
    Window(
        onCloseRequest = {
            controller.close()
            exitApplication()
        },
        title = "UCAS iClass Desktop",
    ) {
        QingxinDesktopApp(controller)
    }
}

private data class DesktopState(
    val account: String = "",
    val password: String = "",
    val rememberLogin: Boolean = false,
    val savedCredentialsAvailable: Boolean = false,
    val session: SchoolSession? = null,
    val courses: List<Course> = emptyList(),
    val selectedCourseId: String? = null,
    val qr: QrSnapshot? = null,
    val busy: Boolean = false,
    val signing: Boolean = false,
    val message: String = "账号和密码仅保存在本次运行的内存中。",
) {
    val selectedCourse: Course?
        get() = courses.firstOrNull { courseKey(it) == selectedCourseId }
}

private class DesktopController(
    private val api: QingxinApiService = QingxinApiService(),
    private val qrTimeline: QrTimelineManager = QrTimelineManager(api),
    private val credentialStore: CredentialStore = WindowsDpapiCredentialStore(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val _state = MutableStateFlow(DesktopState())
    val state: StateFlow<DesktopState> = _state.asStateFlow()

    private var qrJob: Job? = null

    init {
        loadSavedCredentials()
    }

    fun setAccount(value: String) = _state.update { it.copy(account = value) }

    fun setPassword(value: String) = _state.update { it.copy(password = value) }

    fun setRememberLogin(enabled: Boolean) {
        if (enabled) {
            _state.update { it.copy(rememberLogin = true) }
            return
        }

        _state.update { it.copy(rememberLogin = false) }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { credentialStore.clear() }
            }.onSuccess {
                _state.update {
                    it.copy(
                        savedCredentialsAvailable = false,
                        message = "已清除本机保存的登录信息。",
                    )
                }
            }.onFailure {
                _state.update { it.copy(message = "无法清除本机保存的登录信息。") }
            }
        }
    }

    fun clearSavedCredentials() {
        _state.update { it.copy(rememberLogin = false) }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { credentialStore.clear() }
            }.onSuccess {
                _state.update {
                    it.copy(
                        password = "",
                        savedCredentialsAvailable = false,
                        message = "已清除本机保存的登录信息。",
                    )
                }
            }.onFailure {
                _state.update { it.copy(message = "无法清除本机保存的登录信息。") }
            }
        }
    }

    fun login() {
        val account = state.value.account.trim()
        val password = state.value.password
        val rememberLogin = state.value.rememberLogin
        if (account.isEmpty() || password.isEmpty()) {
            _state.update { it.copy(message = "请输入账号和密码。") }
            return
        }
        scope.launch {
            _state.update { it.copy(busy = true, message = "正在登录…") }
            runCatching {
                val session = api.login(account, password)
                val savedCredentials = if (rememberLogin) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            credentialStore.save(StoredCredentials(account, password))
                        }
                    }.isSuccess
                } else {
                    false
                }
                _state.update {
                    it.copy(
                        session = session,
                        password = "",
                        savedCredentialsAvailable = it.savedCredentialsAvailable || savedCredentials,
                        busy = false,
                        message = if (rememberLogin && !savedCredentials) {
                            "登录成功，但无法保存本机登录信息。"
                        } else {
                            "登录成功，正在读取今日课程…"
                        },
                    )
                }
                loadCourses(session)
            }.onFailure { error ->
                _state.update { it.copy(busy = false, message = error.userMessage()) }
            }
        }
    }

    fun refreshCourses() {
        val session = state.value.session ?: return
        scope.launch { loadCourses(session) }
    }

    private suspend fun loadCourses(session: SchoolSession) {
        _state.update { it.copy(busy = true) }
        runCatching {
            val date = LocalDate.now(ShanghaiZone).format(CourseDateFormatter)
            api.getTodayCourses(session, date)
        }.onSuccess { result ->
            val currentSelection = state.value.selectedCourseId
            val selected = result.courses.firstOrNull { courseKey(it) == currentSelection }
                ?: result.courses.firstOrNull()
            _state.update {
                it.copy(
                    courses = result.courses,
                    selectedCourseId = selected?.let(::courseKey),
                    busy = false,
                    message = result.message,
                )
            }
            startQrLoop()
        }.onFailure { error ->
            _state.update { it.copy(busy = false, message = error.userMessage()) }
        }
    }

    fun selectCourse(course: Course) {
        _state.update {
            it.copy(
                selectedCourseId = courseKey(course),
                qr = null,
                message = "已选择 ${course.name}，正在同步学校时间…",
            )
        }
        startQrLoop()
    }

    private fun startQrLoop() {
        qrJob?.cancel()
        qrTimeline.clear()
        val selected = state.value.selectedCourse ?: return
        val qrKey = selected.id.ifBlank { selected.uuid }
        if (qrKey.isBlank()) {
            _state.update { it.copy(message = "该课程缺少可用的课程 ID，无法生成二维码。") }
            return
        }
        qrJob = scope.launch {
            while (true) {
                runCatching { qrTimeline.refreshQr(qrKey) }
                    .onSuccess { snapshot ->
                        _state.update { it.copy(qr = snapshot, message = "二维码已与学校时间同步。") }
                        delay(snapshot.validityDurationMs.coerceAtLeast(500L))
                    }
                    .onFailure { error ->
                        _state.update { it.copy(qr = null, message = error.userMessage()) }
                        delay(5_000L)
                    }
            }
        }
    }

    fun signSelected() {
        val current = state.value
        val session = current.session ?: return
        val course = current.selectedCourse ?: return
        if (course.id.isBlank()) {
            _state.update { it.copy(message = "该课程缺少签到所需的课程 ID。") }
            return
        }
        scope.launch {
            _state.update { it.copy(signing = true, message = "正在提交签到…") }
            runCatching {
                val qr = qrTimeline.refreshQr(course.id.ifBlank { course.uuid })
                api.submitAttendance(session, course.id, qr.schoolTimestampMs)
            }.onSuccess { result ->
                _state.update { old ->
                    val updatedCourses = if (result.outcome == SignOutcome.SIGNED) {
                        old.courses.map { if (courseKey(it) == courseKey(course)) it.copy(signed = true) else it }
                    } else {
                        old.courses
                    }
                    old.copy(courses = updatedCourses, signing = false, message = result.message)
                }
            }.onFailure { error ->
                _state.update { it.copy(signing = false, message = error.userMessage()) }
            }
        }
    }

    fun logout() {
        qrJob?.cancel()
        qrTimeline.clear()
        val current = state.value
        _state.value = DesktopState(
            account = current.account,
            rememberLogin = current.rememberLogin,
            savedCredentialsAvailable = current.savedCredentialsAvailable,
            message = "已退出；本次运行中的会话和密码已清除。",
        )
    }

    fun close() {
        qrJob?.cancel()
        scope.cancel()
    }

    private fun loadSavedCredentials() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { credentialStore.load() }
            }.onSuccess { credentials ->
                if (credentials != null) {
                    _state.update {
                        it.copy(
                            account = credentials.account,
                            password = credentials.password,
                            rememberLogin = true,
                            savedCredentialsAvailable = true,
                            message = "已加载本机保存的登录信息；请确认后登录。",
                        )
                    }
                }
            }.onFailure {
                _state.update { it.copy(message = "无法读取本机保存的登录信息。") }
            }
        }
    }
}

@Composable
private fun QingxinDesktopApp(controller: DesktopController) {
    val state by controller.state.collectAsState()
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = UcasBlue,
            secondary = Color(0xFF40617F),
            surface = Color(0xFFF9FBFD),
            background = Color(0xFFF3F6F9),
        ),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AppHeader(state, controller)
                HorizontalDivider()
                if (state.session == null) {
                    LoginPanel(state, controller)
                } else {
                    CourseWorkspace(state, controller)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(state: DesktopState, controller: DesktopController) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("UCAS iClass Desktop", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = UcasBlue)
            Text("独立 Windows 客户端 · 课程查询与二维码时间轴", color = Color(0xFF5B6570))
        }
        state.session?.let { session ->
            Text("学号 ${session.studentNo}", modifier = Modifier.padding(end = 16.dp))
            if (state.savedCredentialsAvailable) {
                TextButton(onClick = controller::clearSavedCredentials) { Text("清除本机登录") }
            }
            OutlinedButton(onClick = controller::logout) { Text("退出") }
        }
    }
}

@Composable
private fun LoginPanel(state: DesktopState, controller: DesktopController) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(460.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("登录轻新课堂", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text("支持 SEP 邮箱或轻新课堂学号。默认不保存；可选仅为当前 Windows 账户加密保存登录信息。")
                OutlinedTextField(
                    value = state.account,
                    onValueChange = controller::setAccount,
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = controller::setPassword,
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.rememberLogin,
                        onCheckedChange = controller::setRememberLogin,
                    )
                    Text("在此 Windows 账户中记住登录")
                }
                if (state.savedCredentialsAvailable) {
                    TextButton(onClick = controller::clearSavedCredentials) {
                        Text("清除本机保存的登录信息")
                    }
                }
                Button(
                    onClick = controller::login,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("登录并读取今日课程")
                }
                StatusMessage(state.message)
            }
        }
    }
}

@Composable
private fun CourseWorkspace(state: DesktopState, controller: DesktopController) {
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(390.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今日课程", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = controller::refreshCourses, enabled = !state.busy) { Text("刷新") }
            }
            Spacer(Modifier.height(12.dp))
            if (state.courses.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.busy) "正在读取课程…" else "今天没有可显示的课程")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.courses, key = ::courseKey) { course ->
                        CourseCard(course, courseKey(course) == state.selectedCourseId) {
                            controller.selectCourse(course)
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            CourseDetail(state, controller)
        }
    }
}

@Composable
private fun CourseCard(course: Course, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) UcasBlueSoft else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(course.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (course.signed) "已签到" else "未签到",
                    color = if (course.signed) SuccessGreen else Color(0xFF8A4B08),
                )
            }
            Text("${formatTime(course.beginTime)}–${formatTime(course.endTime)}", color = Color(0xFF4E5964))
            Text(course.teacher.ifBlank { "教师未知" }, color = Color(0xFF707A84), fontSize = 13.sp)
        }
    }
}

@Composable
private fun CourseDetail(state: DesktopState, controller: DesktopController) {
    val course = state.selectedCourse
    if (course == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("请选择一门课程") }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(course.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("${formatTime(course.beginTime)}–${formatTime(course.endTime)} · ${course.teacher.ifBlank { "教师未知" }}")
        Spacer(Modifier.height(20.dp))
        QrPanel(state.qr)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = controller::signSelected,
            enabled = !state.signing && !course.signed && course.id.isNotBlank(),
            modifier = Modifier.width(280.dp),
        ) {
            if (state.signing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (course.signed) "已签到" else "立即签到")
        }
        Spacer(Modifier.height(16.dp))
        StatusMessage(state.message)
        Spacer(Modifier.height(12.dp))
        Text(
            "二维码约每 5 秒更新一次，并在 30 秒学校校时样本到期后重新校时。",
            color = Color(0xFF6D7781),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun QrPanel(snapshot: QrSnapshot?) {
    if (snapshot == null) {
        Box(
            modifier = Modifier.size(280.dp).background(Color(0xFFF0F3F6), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    QrCode(snapshot.url)
    Spacer(Modifier.height(8.dp))
    SelectionContainer {
        Text(snapshot.url, color = Color(0xFF6A737D), fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun QrCode(content: String) {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256)
    Canvas(Modifier.size(280.dp).background(Color.White).padding(12.dp)) {
        val cellWidth = size.width / matrix.width
        val cellHeight = size.height / matrix.height
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cellWidth, y * cellHeight),
                        size = Size(cellWidth + 0.2f, cellHeight + 0.2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String) {
    Surface(color = Color(0xFFF2F5F8), shape = RoundedCornerShape(8.dp)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFF44505C))
    }
}

private fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message ?: "学校接口返回异常（$code）"
    else -> message?.takeIf { it.isNotBlank() } ?: "操作失败，请检查网络后重试。"
}

private fun courseKey(course: Course): String = course.id.ifBlank { course.uuid }

private fun formatTime(raw: String): String {
    val normalized = raw.trim().replace('：', ':')
    val match = Regex("""(\d{1,2}):(\d{2})""").find(normalized) ?: return normalized.ifBlank { "—" }
    return "%02d:%02d".format(match.groupValues[1].toInt(), match.groupValues[2].toInt())
}
