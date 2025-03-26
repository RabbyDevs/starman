import com.github.kittinunf.fuel.httpGet
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.io.File
import java.io.FileOutputStream
import java.lang.instrument.Instrumentation
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.swing.*

private fun randomUUID() = UUID.randomUUID().toString()

private fun backup(folder: String) {
    val backupFolder = File("${folder}_BackupStarman")
    if (!backupFolder.exists()) {
        backupFolder.mkdir()
    }

    File(folder).listFiles()?.forEach { file ->
        if (file.name == backupFolder.name) return@forEach

        file.copyTo(File("${backupFolder.path}/${file.name}"), overwrite = true)
        file.delete()
    }
}

private fun generateText(text: String): String {
    return "<html><div style='text-align: center;'>$text</div></html>"
}

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Starman: Modpack Manager from Hell.")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(400, 350)

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = Color.BLACK

        val statusLabel = JLabel(generateText("Not started"))
        statusLabel.foreground = Color.WHITE
        statusLabel.alignmentX = Component.CENTER_ALIGNMENT
        statusLabel.verticalAlignment = SwingConstants.TOP
        statusLabel.horizontalAlignment = SwingConstants.CENTER
        statusLabel.preferredSize = Dimension(200, 100)

        val loadingIcon = Thread.currentThread().contextClassLoader.getResource("loading.gif")?.let { ImageIcon(it) }
        val loadingLabel = if (loadingIcon != null) JLabel(loadingIcon) else JLabel("Loading...")
        loadingLabel.alignmentX = Component.CENTER_ALIGNMENT

        panel.add(loadingLabel)
        panel.add(statusLabel)

        frame.contentPane.add(panel)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        Thread {
            syncModpack(statusLabel, frame)
        }.start()
    }
}

fun syncModpack(statusLabel: JLabel, frame: JFrame) {
    val folders = arrayOf("mods", "config")
    val noBackupFolders = arrayOf("config")

    folders.forEach { folder ->
        val hashFile = File("$folder-hash.txt")
        if (!hashFile.exists()) hashFile.writeText("no hash")
        
        val folderFile = File(folder)
        if (!folderFile.exists() || !folderFile.isDirectory) {
            statusLabel.text = generateText("Error: $folder does not exist. Creating...")
            folderFile.mkdir()
            TimeUnit.SECONDS.sleep(2)
            return@forEach
        }

        val hashUrl = "https://mcfiles.starfall-studios.com/files/$folder-hash.txt"
        val hashResponse = hashUrl.httpGet().response()
        val hash = hashResponse.second.data
        val outputFile = File("$folder-server-hash.txt")
        outputFile.writeBytes(hash)

        if (outputFile.readText() == hashFile.readText()) {
            statusLabel.text = generateText("$folder is already synced, skipping...")
            return@forEach
        }

        if (!noBackupFolders.contains(folder)) {
            statusLabel.text = generateText("Backing up $folder...")
            backup(folder)
            statusLabel.text = generateText("Backup complete.")
            TimeUnit.SECONDS.sleep(1)
        }

        statusLabel.text = generateText("Downloading $folder.zip...")
        val url = "https://mcfiles.starfall-studios.com/files/$folder.zip"
        val response = url.httpGet().response()

        if (response.second.statusCode == 200) {
            val zipFile = File("$folder.zip")
            zipFile.writeBytes(response.second.data)

            hashFile.writeText(hashFile.hash())
            extractZip(zipFile, folder)
            zipFile.delete()

            statusLabel.text = generateText("Finished syncing $folder.")
        } else {
            statusLabel.text = generateText("Error: Failed to download $folder.zip.")
            TimeUnit.SECONDS.sleep(5)
            frame.dispose()
        }
    }
    statusLabel.text = generateText("Fully synced.")
    TimeUnit.SECONDS.sleep(3)
    frame.dispose()
}

fun extractZip(zipFile: File, destinationFolder: String) {
    ZipFile(zipFile).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val targetFile = File(destinationFolder, entry.name)

            if (entry.isDirectory) {
                targetFile.mkdirs()
            } else {
                targetFile.parentFile.mkdirs()
                zip.getInputStream(entry).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

fun File.hash(): String {
    val digest = MessageDigest.getInstance("SHA-512")
    return digest.digest(this.readBytes()).joinToString("") { "%02x".format(it) }
}

object Agent {
    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        println("Starman loaded successfully.")
        main()
    }
}