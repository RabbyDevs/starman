import java.io.File
import java.util.zip.ZipFile
import java.util.UUID
import kotlin.system.exitProcess

fun randomUUID() = UUID.randomUUID().toString()

fun backup(folder: String) {
    if (File(folder).listFiles()?.isNotEmpty() == true) {
        val modsList = File(folder).listFiles()
        val modsBackup = File("$folder/${folder}BackupStarfall-${randomUUID()}")
        if (!modsBackup.exists()) { modsBackup.mkdir() }
        modsList.forEach { file ->
            file.let { sourceFile ->
                sourceFile.copyTo(File("${modsBackup.path}/${file.name}"))
            }
            file.delete()
        }
    }
}

fun main() {
    val folders: Array<String> = arrayOf("mods")
    folders.forEach { folder ->
        if (!File(folder).exists() or !File(folder).isDirectory) {
            println("$folder folder does not exist or is not a folder.")
            exitProcess(-2)
        }
    }
    folders.forEach { folder ->
        println("backing up $folder")
        backup(folder)
        println("finished backing up $folder")
    }

    println("unzipping")
    ZipFile("mods.zip").use { zip ->
        zip.entries().asSequence().forEach { entry ->
            zip.getInputStream(entry).use { input ->
                if (entry.isDirectory) {
                    File("mods/${entry.name}").mkdir()
                } else {
                    File("mods/${entry.name}").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    println("unzip done")
}