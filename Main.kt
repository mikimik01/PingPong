import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.Math.abs
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.system.exitProcess

var ping = 1
var pong = -1
var m = 0
var criticalMessage = 0

lateinit var sfd2: Socket
val lock = ReentrantLock()

lateinit var ip: String
lateinit var port: String
lateinit var listenSocket: String

fun regenerate(x: Int) {
    ping = abs(x)
    pong = -ping
}

fun incarnate(x: Int) {
    ping = abs(x) + 1
    pong = -ping
}

fun sendPing() {
    try {
        val out = DataOutputStream(sfd2.getOutputStream())
        out.writeInt(ping)
        println("Wysłano: PING $ping")
    } catch (e: Exception) {
        println("Błąd przy wysyłaniu PING: ${e.message}")
    }
}

fun sendPong() {
    try {
        val out = DataOutputStream(sfd2.getOutputStream())
        out.writeInt(pong)
        println("Wysłano: PONG $pong")
    } catch (e: Exception) {
        println("Błąd przy wysyłaniu PONG: ${e.message}")
    }
}

fun criticalSection() {
    println("Sekcja krytyczna")
    Thread.sleep(5000)
    sendPing()

    lock.withLock {
        m = ping
        criticalMessage = 0
    }
}

fun main(args: Array<String>) {
    if (args.size != 3 && args.size != 4) {
        println("Niepoprawna liczba argumentów.")
        exitProcess(-1)
    }

    ip = args[0]
    port = args[1]
    listenSocket = args[2]
    var starting = (args.size == 4)

    val addr = InetAddress.getByName(ip)
    val portInt = port.toInt()
    val listenPort = listenSocket.toInt()

    val serverSocket = ServerSocket(listenPort)
    var message = 0
    if (starting) {
        println("Próba połączenia z $ip:$port...")
        while (true) {
            try {
                sfd2 = Socket(addr, portInt)
                break
            } catch (e: Exception) {
                println("Connect nieudane. Ponawianie...: ${e.message}")
                Thread.sleep(1000)
            }
        }
        println("Połączono z $ip:$port!")
        lock.withLock {
            try {
                val out = DataOutputStream(sfd2.getOutputStream())
                out.writeInt(pong)
                println("Wysłano: PONG $pong")
                m = pong
            } catch (e: Exception) {
                println("Błąd początkowego wysłania: ${e.message}")
            }
        }
        message = ping
    }

    val cfd = serverSocket.accept()
    val inputFromPeer = DataInputStream(cfd.getInputStream())

    if (!starting) {
        println("Attempting to connect to $ip using port $port")
        while (true) {
            try {
                sfd2 = Socket(addr, portInt)
                break
            } catch (e: Exception) {
                println("Connect nieudane. Ponawianie...: ${e.message}")
                Thread.sleep(1000)
            }
        }
        println("Connected to $ip using port $port")
    }

    while (true) {
        if (!starting) {
            message = try {
                inputFromPeer.readInt()
            } catch (e: Exception) {
                println("Błąd odczytu: ${e.message}")
                break
            }
        }else{
            starting = false
        }

        if (message > 0) {  // Odebrano PING
            println("PING $message")
            lock.withLock {
                if (m == message) regenerate(message)
                else if(abs(m) > abs(message)) return@withLock
                ping = message
                if (criticalMessage == 0) {
                    criticalMessage = message
                    thread(start = true) {
                        criticalSection()
                    }
                }
            }
        } else if (message < 0) {  // Odebrano PONG
            println("PONG $message")
            lock.withLock {
                if (m == message) regenerate(message)
                else if (abs(m) > abs(message)) return@withLock
                pong = message
                if (criticalMessage != 0) {
                    incarnate(ping)
                }
            }
            Thread.sleep(1000)
            lock.withLock {
                sendPong()
                m = pong
            }
        }
    }

    serverSocket.close()
    cfd.close()
    sfd2.close()
}
