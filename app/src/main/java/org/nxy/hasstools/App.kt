package org.nxy.hasstools

import android.app.Application
import android.content.Context
import android.util.Log
import org.nxy.hasstools.utils.NetworkMonitor
import org.nxy.hasstools.utils.amap.AMap
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler() : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        LogInterceptor.log("CrashHandler", "Uncaught exception: ${throwable.stackTraceToString()}")
        // 可选：交由系统默认处理
        defaultHandler?.uncaughtException(thread, throwable)
    }
}

object LogInterceptor {
    private var logFile: File? = null
    private var logWriter: BufferedWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH", Locale.getDefault())
    private var currentHour: String = getCurrentHour()
    private lateinit var logDir: File

    fun init(context: Context) {
        logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) logDir.mkdirs()

        // 重定向 System.out 和 System.err
        redirectSystemStreams()

        // 初始化日志文件
        setupLogFile()
    }

    private fun redirectSystemStreams() {
        val logOutputStream = object : OutputStream() {
            private val buffer = ByteArrayOutputStream()

            override fun write(b: Int) {
                if (b.toChar() == '\n') {
                    val message = buffer.toString("UTF-8")
                    buffer.reset()
                    log("System.out", message)
                } else {
                    buffer.write(b)
                }
            }
        }

        val utf8PrintStream = PrintStream(logOutputStream, true, "UTF-8")
        System.setOut(utf8PrintStream)
        System.setErr(utf8PrintStream)
    }

    private fun setupLogFile() {
        val newHour = getCurrentHour()
        if (newHour != currentHour || logFile == null) {
            closeLogFile()
            currentHour = newHour
            logFile = File(logDir, "log_$currentHour.txt")

            // 确保写入时使用 UTF-8 编码
            logWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(logFile, true), "UTF-8"))

            cleanOldLogs()
        }
    }

    private fun getCurrentHour(): String {
        return dateFormat.format(Date())
    }

    private fun closeLogFile() {
        logWriter?.flush()
        logWriter?.close()
    }

    private fun cleanOldLogs() {
        val files = logDir.listFiles { _, name -> name.startsWith("log_") }
        if (files != null && files.size > 48) {
            files.sortedBy { it.lastModified() }.take(files.size - 48).forEach { it.delete() }
        }
    }

    internal fun log(tag: String, message: String) {
        setupLogFile()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "$timestamp [$tag]: $message"

        Log.i(tag, message) // Log to Android logcat

        logWriter?.write(logMessage)
        logWriter?.newLine()
        logWriter?.flush()
    }

    fun d(tag: String, message: String) = log(tag, "[DEBUG] $message")
    fun i(tag: String, message: String) = log(tag, "[INFO] $message")
    fun w(tag: String, message: String) = log(tag, "[WARN] $message")
    fun e(tag: String, message: String) = log(tag, "[ERROR] $message")
    fun v(tag: String, message: String) = log(tag, "[VERBOSE] $message")
}

class App : Application() {
    companion object {
        private var _appContext: Context? = null

        private var _appInstance: Application? = null

        val context: Context
            get() = _appContext
                ?: throw IllegalStateException("AppContext accessed before Application.onCreate()")

        val instance: Application
            get() = _appInstance
                ?: throw IllegalStateException("AppInstance accessed before Application.onCreate()")
    }

    override fun onCreate() {
        super.onCreate()

        _appContext = applicationContext

        _appInstance = this

        LogInterceptor.init(this)

        // 设置全局异常处理
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())

        // 初始化网络监控
        NetworkMonitor.load()

        // 初始化高德SDK
        AMap.init()
    }
}
