
import com.github.kittinunf.fuel.httpGet
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.io.File
import java.io.FileOutputStream
import java.lang.instrument.Instrumentation
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.swing.*

object Agent {
    @JvmStatic
    fun main() {
        val frame = JFrame("Starman: Modpack Manager from Hell.")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(400, 350)

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = Color.BLACK

        val statusLabel = JLabel("<html>Not started</html>")
        statusLabel.foreground = Color.WHITE
        statusLabel.alignmentX = Component.CENTER_ALIGNMENT
        statusLabel.preferredSize = Dimension(200, 100)

        val loadingIcon = ImageIcon(object {}.javaClass.getResource("/loading.gif"))
        val loadingLabel = JLabel(loadingIcon)
        loadingLabel.alignmentX = Component.CENTER_ALIGNMENT

        panel.add(loadingLabel)
        panel.add(statusLabel)

        frame.contentPane.add(panel)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        val folders = arrayOf("mods")
        folders.forEach { folder ->
            if (!File(folder).exists() or !File(folder).isDirectory) {
                statusLabel.text = "Error: $folder folder does not exist or is not a directory."
                TimeUnit.SECONDS.sleep(3)
                frame.dispose()
                return@forEach
            }
            statusLabel.text = "Backing up $folder."
            backup(folder)
            statusLabel.text = "Finished backing up $folder."
            TimeUnit.SECONDS.sleep(1)
            statusLabel.text = "Getting $folder files from server..."
            val url = "https://mcfiles.starfall-studios.com/files/$folder.zip"
            val response = url.httpGet().response()
            if (response.second.statusCode == 200) {
                statusLabel.text = "$folder files obtained, unzipping and placing them."
                val responseBody = response.second.data
                val outputFile = File("$folder.zip")
                FileOutputStream(outputFile).use {
                    it.write(responseBody)
                }
                ZipFile("$folder.zip").use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        zip.getInputStream(entry).use { input ->
                            if (entry.isDirectory) {
                                File("$folder/${entry.name}").mkdir()
                            } else {
                                File("$folder/${entry.name}").outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
                outputFile.delete()
                statusLabel.text = "Finished syncing $folder."
            } else {
                statusLabel.text = "Error: Failed to download $folder.zip. Status code: ${response.second.statusCode}"
                TimeUnit.SECONDS.sleep(5)
                frame.dispose()
                return@forEach
            }
        }

        statusLabel.text = "Fully synced. Exiting in 3 seconds."
        TimeUnit.SECONDS.sleep(3)
        frame.dispose()
    }

    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        println("Agent loaded successfully")
        main()
    }

    private fun randomUUID() = UUID.randomUUID().toString()

    private fun backup(folder: String) {
        val modsBackup = File("$folder/${folder}BackupStarfall-${randomUUID()}")
        if (!modsBackup.exists()) {
            modsBackup.mkdir()
        }
        File(folder).listFiles()?.forEach { file ->
            file.copyTo(File("${modsBackup.path}/${file.name}"))
            file.delete()
        }
    }
}
