/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spongepowered.configurate.tool

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.TermColors
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.SimpleConfigurationNode
import org.spongepowered.configurate.ValueType
import org.spongepowered.configurate.attributed.AttributedConfigurationNode
import org.spongepowered.configurate.attributed.SimpleAttributedConfigurationNode
import org.spongepowered.configurate.commented.CommentedConfigurationNode
import org.spongepowered.configurate.commented.SimpleCommentedConfigurationNode
import org.spongepowered.configurate.gson.GsonConfigurationLoader
import org.spongepowered.configurate.hocon.HOCONConfigurationLoader
import org.spongepowered.configurate.loader.ConfigurationLoader
import org.spongepowered.configurate.loader.HeaderMode
import org.spongepowered.configurate.xml.XMLConfigurationLoader
import org.spongepowered.configurate.yaml.YAMLConfigurationLoader
import org.yaml.snakeyaml.DumperOptions
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems


private val t = TermColors()

val HAS_UTF8 = Charset.defaultCharset() == StandardCharsets.UTF_8

const val INDENT = "  "

val CHILD_NODE = t.brightCyan(if (HAS_UTF8) "┣" else "+")
val CHILD_CONT = t.brightCyan(if (HAS_UTF8) "┃" else "|")
val CHILD_END = t.brightCyan(if (HAS_UTF8) "┗" else "L")
val SPLIT = t.bold(if (HAS_UTF8) "╺" else "-")

fun heading(text: String) = t.bold(t.brightBlue(text))

class Tool : CliktCommand(help = """
    This tool displays the Configurate data structures read from a config file
    
    This helps to understand how Configurate understands its data
""".trimIndent()) {
    init {
        versionOption(this::class.java.`package`.implementationVersion)
        context {
            autoEnvvarPrefix = "CONFIGURATE"
        }
        subcommands(Xml(), Yaml(), Json(), Hocon())
    }
    override fun run() = Unit
}

sealed class FormatSubcommand<N: ConfigurationNode<N>>(formatName: String): CliktCommand(help = "Display files that are in $formatName format") {
    val path by argument(help="Location of the file to read").path(mustExist = true, canBeDir = false)
    val header by option("--header-mode", help="How to read a header from this file").enum<HeaderMode>().default(HeaderMode.PRESERVE)

    /**
     * Create a new loader instance based on provided arguments
     */
    abstract fun createLoader(): ConfigurationLoader<N>

    override fun run() {
        echo("Current dir: ${FileSystems.getDefault().getPath(".").toAbsolutePath()}")
        val loader = createLoader()
        try {
            val node = loader.load()
            echo("Reading from ${t.brightBlue(path.toString())}")
            val header = node.options.header
            if (header != null) {
                echo(heading("Header") + " $SPLIT " + header.replace("\n", "\n$CHILD_CONT "))
                echo("")
            }
            dumpTree(node, "")
        } catch (e: IOException) {
            throw CliktError("Unable to load configuration", e)
        }
    }

    private fun dumpTree(node: N, prefix: String = "") {
        fun write(vararg content: Any?) {
            echo(prefix + content.joinToString(" "))
        }

        fun enterChild(iter: Iterator<*>, child: N): String {
            val childPrefix: String
            val entryPrefix: String

            if (iter.hasNext()) {
                childPrefix = "$CHILD_CONT  "
                entryPrefix = CHILD_NODE
            } else {
                childPrefix = INDENT
                entryPrefix = CHILD_END
            }

            write(entryPrefix, heading(child.key?.toString() ?: "(unnamed)"), SPLIT, child.valueType)
            return prefix + childPrefix
        }

        if (node is AttributedConfigurationNode<*>) {
            write(heading("Tag name"), SPLIT, node.tagName)
            val attributes = node.attributes
            if (!attributes.isEmpty()) {
                write(heading("Attributes"), SPLIT, attributes.map { (k, v) -> t.gray("\"${t.white(k)}\"=\"${t.white(v)}\"") }.joinToString(", "))
            }
        }
        if (node is CommentedConfigurationNode<*>) {
            node.comment.ifPresent {
                write(heading("Comment"), SPLIT, it)
            }
        }
        when (node.valueType) {
            ValueType.LIST -> node.childrenList.iterator().also {
                while (it.hasNext()) {
                    val child = it.next()
                    val nextPrefix = enterChild(it, child)
                    dumpTree(child, nextPrefix)
                }
            }
            ValueType.MAP -> node.childrenMap.iterator().also {
                while (it.hasNext()) {
                    val (_, value) = it.next()
                    val nextPrefix = enterChild(it, value)

                    dumpTree(value, nextPrefix)
                }
            }
            ValueType.SCALAR -> {
                val value = node.value!!
                write(heading("Value"), SPLIT,
                        value.toString().replace(Regex("(\r?\n)"), "$1$prefix    "), t.gray("(a ${value::class.qualifiedName})"))
            }
            ValueType.NULL -> write(heading("Value: "), SPLIT, t.gray("(null)"))
        }
    }
}

class Xml : FormatSubcommand<SimpleAttributedConfigurationNode>("XML") {
    private val indent by option("-i", "--indent", help = "How much to indent when outputting").int().default(2)
    private val includeXmlDeclaration by option("-x", "--xml-declaration", help = "Whether to include the XML declaration").flag("-X", default = true)
    private val writeExplicitType by option("-t", "--explicit-type", help = "Include explicit type information").flag("-T", default = false)

    // TODO: Schemas

    override fun createLoader(): ConfigurationLoader<SimpleAttributedConfigurationNode> {
        return XMLConfigurationLoader.builder()
                .setPath(this.path)
                .setHeaderMode(header)
                .setIndent(indent)
                .setIncludeXmlDeclaration(includeXmlDeclaration)
                .setWriteExplicitType(writeExplicitType)
                .build()
    }
}

class Yaml: FormatSubcommand<SimpleConfigurationNode>("YAML") {
    private val indent by option("-i", "--indent", help = "How much to indent when outputting").int().default(4)
    private val flowStyle by option("-f", "--flow", help = "What flow style to use").enum<DumperOptions.FlowStyle>().default(DumperOptions.FlowStyle.AUTO)
    override fun createLoader(): ConfigurationLoader<SimpleConfigurationNode> {
        return YAMLConfigurationLoader.builder()
                .setPath(path)
                .setHeaderMode(header)
                .setIndent(indent)
                .setFlowStyle(flowStyle)
                .build()
    }
}

class Json: FormatSubcommand<SimpleConfigurationNode>("JSON") {
    private val lenient by option("-l", "--lenient", help = "Parse JSON leniently").flag("-L", "--strict", default = true)
    private val indent by option("-i", "--indent", help = "How much to indent when outputting").int().default(4)
    override fun createLoader(): ConfigurationLoader<SimpleConfigurationNode> {
        return GsonConfigurationLoader.builder()
                .setPath(path)
                .setHeaderMode(header)
                .setLenient(lenient)
                .setIndent(indent)
                .build()
    }
}

class Hocon: FormatSubcommand<SimpleCommentedConfigurationNode>("HOCON") {

    override fun createLoader(): ConfigurationLoader<SimpleCommentedConfigurationNode> {
        return HOCONConfigurationLoader.builder()
                .setHeaderMode(header)
                .setPath(path)
                .build()
    }
}

fun main(args: Array<String>) = Tool()
        .main(args)
