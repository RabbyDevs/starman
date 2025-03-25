
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
    val backupFolder = File("$folder/${folder}BackupStarman")
    if (!backupFolder.exists()) {
        backupFolder.mkdir()
    }
    File(folder).listFiles()?.forEach { file ->
        file.copyTo(File("${backupFolder.path}/${file.name}"))
        file.delete()
    }
}

private fun generateText(text: String): String {
    return "<html><div style='text-align: center;'>$text</div></html>"
}

fun main() {
    val frame = JFrame("Starman: Modpack Manager from Hell.")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(400, 350)

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.background = Color.BLACK

    val statusLabel = JLabel(generateText("Not started"))
    statusLabel.foreground = Color.WHITE
    statusLabel.alignmentX = Component.CENTER_ALIGNMENT
    statusLabel.verticalAlignment = SwingConstants.TOP // Align text to the top
    statusLabel.horizontalAlignment = SwingConstants.CENTER // Align text to the center horizontally
    statusLabel.preferredSize = Dimension(200, 100)

    val loadingIcon = ImageIcon(object {}.javaClass.getResource("/loading.gif"))
    val loadingLabel = JLabel(loadingIcon)
    loadingLabel.alignmentX = Component.CENTER_ALIGNMENT

    panel.add(loadingLabel)
    panel.add(statusLabel)

    frame.contentPane.add(panel)
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    val folders = arrayOf("mods", "config", "resourcepacks")
    val noBackupFolders = arrayOf("config")
    folders.forEach { folder ->
        val hashFile = File("$folder-hash.txt")
        if (!hashFile.exists()) {
            hashFile.writeText("no hash")
        }
        if (!File(folder).exists() or !File(folder).isDirectory) {
            statusLabel.text = generateText("Error: $folder folder does not exist or is not a directory, automatically creating it if it doesn't exist.")
            if (!File(folder).exists()) {File(folder).mkdir()}
            TimeUnit.SECONDS.sleep(3)
            return@forEach
        }
        val hashUrl = "https://mcfiles.starfall-studios.com/files/$folder-hash.txt"
        val hashResponse = hashUrl.httpGet().response()
        val hash = hashResponse.second.data
        val outputFile = File("$folder-server-hash.txt")
        FileOutputStream(outputFile).use {
            it.write(hash)
        }
        if (outputFile.readText() == hashFile.readText()) {
            statusLabel.text = generateText("$folder is already synced, skipping...")
            return@forEach
        }
        if (!noBackupFolders.contains(folder)) {
            statusLabel.text = generateText("Backing up $folder.")
            backup(folder)
            statusLabel.text = generateText("Finished backing up $folder.")
            TimeUnit.SECONDS.sleep(1)
        }
        statusLabel.text = generateText("Getting $folder files from server...")
        val url = "https://mcfiles.starfall-studios.com/files/$folder.zip"
        val response = url.httpGet().response()
        if (response.second.statusCode == 200) {
            statusLabel.text = generateText("$folder files obtained, unzipping and placing them.")
            val responseBody = response.second.data
            val outputFile = File("$folder.zip")
            FileOutputStream(outputFile).use {
                it.write(responseBody)
            }
            hashFile.writeText(HashUtils.getCheckSumFromFile(MessageDigest.getInstance(MessageDigestAlgorithm.SHA_512), outputFile))
            ZipFile("$folder.zip").use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        if (entry.isDirectory) {
                            if (!File("$folder/${entry.name}").exists()) File("$folder/${entry.name}").mkdir()
                        } else {
                            File("$folder/${entry.name}").outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            outputFile.delete()
            statusLabel.text = generateText("Finished syncing $folder.")
        } else {
            statusLabel.text = generateText("Error: Failed to download $folder.zip. Status code: ${response.second.statusCode}")
            TimeUnit.SECONDS.sleep(5)
            frame.dispose()
            return@forEach
        }
    }

    statusLabel.text = generateText("Fully synced.")
    frame.dispose()
}

object Agent {

    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        println("Starman loaded successfully.")
        main()
    }
}
