import com.github.kittinunf.fuel.httpGet
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

private fun isMacOS(): Boolean {
    return System.getProperty("os.name").lowercase().contains("mac")
}

fun main() {
    val useUI = !isMacOS()
    
    fun log(message: String) {
        if (useUI) {
            println(message) // Placeholder for UI update logic if needed later
        } else {
            println(message)
        }
    }
    
    log("Starting Starman: Modpack Manager")

    val folders = arrayOf("mods", "config")
    val noBackupFolders = arrayOf("config")
    
    folders.forEach { folder ->
        val hashFile = File("$folder-hash.txt")
        if (!hashFile.exists()) {
            hashFile.writeText("no hash")
        }
        if (!File(folder).exists() or !File(folder).isDirectory) {
            log("Error: $folder folder does not exist or is not a directory, automatically creating it if necessary.")
            if (!File(folder).exists()) { File(folder).mkdir() }
            TimeUnit.SECONDS.sleep(3)
            return@forEach
        }
        
        val hashUrl = "https://mcfiles.starfall-studios.com/files/$folder-hash.txt"
        val hashResponse = hashUrl.httpGet().response()
        val hash = hashResponse.second.data
        val outputFile = File("$folder-server-hash.txt")
        FileOutputStream(outputFile).use { it.write(hash) }
        
        if (outputFile.readText() == hashFile.readText()) {
            log("$folder is already synced, skipping...")
            return@forEach
        }
        
        if (!noBackupFolders.contains(folder)) {
            log("Backing up $folder.")
            backup(folder)
            log("Finished backing up $folder.")
            TimeUnit.SECONDS.sleep(1)
        }
        
        log("Getting $folder files from server...")
        val url = "https://mcfiles.starfall-studios.com/files/$folder.zip"
        val response = url.httpGet().response()
        
        if (response.second.statusCode == 200) {
            log("$folder files obtained, unzipping and placing them.")
            val responseBody = response.second.data
            val zipFile = File("$folder.zip")
            FileOutputStream(zipFile).use { it.write(responseBody) }
            
            hashFile.writeText(HashUtils.getCheckSumFromFile(MessageDigest.getInstance(MessageDigestAlgorithm.SHA_512), zipFile))
            
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        val targetFile = File(folder, entry.name.removePrefix("$folder/"))
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile.mkdirs()
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            
            zipFile.delete()
            log("Finished syncing $folder.")
        } else {
            log("Error: Failed to download $folder.zip. Status code: ${response.second.statusCode}")
            TimeUnit.SECONDS.sleep(5)
            return@forEach
        }
    }

    log("Fully synced.")
}

object Agent {
    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        println("Starman loaded successfully.")
        main()
    }
}