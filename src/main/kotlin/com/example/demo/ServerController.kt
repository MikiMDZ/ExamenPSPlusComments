package com.example.demo

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.SecretKeySpec

@RestController
class ServerController(
    private val adminrepositorio: UserAdminRepository,
    private val userrepositorio: UserRepository,
    private val mensajeRepository: MensajeRepository
) {
    val type = "AES/ECB/PKCS5Padding"

    //curl --request POST  --header "Content-type:application/json; charset=utf-8" --data "{\"nombre\":\"U4\",\"pass\":\"123\"}" localhost:8083/crearUsuario
    fun clavealeatorio(): String {
        val tamano = 20
        var cadena = ""
        val numeros = (0..9)
        for (i in 0 until tamano) {

            cadena += numeros.random()
        }
        return cadena
    }


    @PostMapping("crearUsuario")
    fun crearUsuario(@RequestBody usuario: Usuario): Any? {
        var retorno: Any? = null
        var bool = false
        if (userrepositorio.findAll().size < 1) {

            val usuario = User(usuario.nombre, usuario.pass, clavealeatorio())
            userrepositorio.save(usuario)
            retorno = usuario.clavecifrado
        } else {
            userrepositorio.findAll().forEach {
                if (it.nombre == usuario.nombre) {
                    if (it.pass == usuario.pass) {
                        retorno = it.clavecifrado
                    } else {
                        retorno = Errortype(1, "Pass invalida")
                    }
                    bool = true
                }

            }
            if (!bool) {
                val usuario = User(usuario.nombre, usuario.pass, clavealeatorio())
                userrepositorio.save(usuario)
                retorno = usuario.clavecifrado
            }
        }

        return retorno
    }

    @PostMapping("crearMensaje")
    fun crearMensaje(@RequestBody mensaje: Mensaje): Any? {
        var retorno: Any? = null
        var bool = false
        userrepositorio.findAll().forEach {
            if (it.nombre == mensaje.usuarioId) {
                mensajeRepository.save(Mensaje(mensaje.texto, mensaje.usuarioId))
                retorno = "Success"
                bool = true

            }

        }
        if (!bool) retorno = Errortype(2, "Usuario inexistente")
        return retorno


    }

    @GetMapping("descargarMensajes")
    fun descargarMensajes(): Retorno {
        val lista = mensajeRepository.findAll()
        val retorno = Retorno(lista)
        /*val gson = Gson()
        return gson.toJson(retorno)*/
        return retorno


    }

    @GetMapping("descargarMensajesFiltrados")
    fun descargar(@RequestBody mensaje: String): Retorno {
        val lista = mutableListOf<Mensaje>()

        mensajeRepository.findAll().forEach {
            if (it.texto.contains(mensaje))
                lista.add(it)
        }
        val retorno = Retorno(lista)
        return retorno
    }

    @GetMapping("obtenerMensajesYLlaves")
    fun obtenermensajesyllaves(@RequestBody usuario: Usuario): Any {

        if (adminrepositorio.findAll()[0].Nombre == usuario.nombre && adminrepositorio.findAll()[0].Pass == usuario.pass) {
            val lista = mutableListOf<Userfiltrado>()
            userrepositorio.findAll().forEach {
                lista.add(Userfiltrado(it.nombre, it.clavecifrado))
            }
            val retorno = RetornoAdmin(lista)
            return retorno
        } else return Errortype(3, "Pass de administrador incorrecta")

    }

    @GetMapping("obtenerMensajesDescifrados")
    fun obtenerdescifrados(@RequestBody usuario: Usuario): Any {
        val listaa = mutableListOf<MensajeAdmin>()
        return if (adminrepositorio.findAll()[0].Nombre == usuario.nombre && adminrepositorio.findAll()[0].Pass == usuario.pass) {
            mensajeRepository.findAll().forEach {
                try {
                    it.texto = descifrar(it.texto, obtenerclavecifrado(it.usuarioId))
                } catch (e: IllegalBlockSizeException) {
                    it.texto = "Texto indescifrable"
                }
                listaa.add(MensajeAdmin(it.texto, it.usuarioId))
            }
            val retorno = RetornoMensajes(listaa)
            retorno
        } else Errortype(3, "Pass de administrador incorrecta")

    }

    fun obtenerclavecifrado(user: String): String {
        var clave = ""
        userrepositorio.findAll().forEach {
            if (it.nombre == user)
                clave = it.clavecifrado
        }
        return clave
    }


    private fun cifrar(textoEnString: String, llaveEnString: String): String {
        //println("Voy a cifrar: $textoEnString")
        val cipher = Cipher.getInstance(type)
        cipher.init(Cipher.ENCRYPT_MODE, getKey(llaveEnString))
        val textCifrado = cipher.doFinal(textoEnString.toByteArray(Charsets.UTF_8))
        //println("Texto cifrado $textCifrado")
        val textCifradoYEncodado = Base64.getUrlEncoder().encodeToString(textCifrado)
        //println("Texto cifrado y encodado $textCifradoYEncodado")
        return textCifradoYEncodado
        //return textCifrado.toString()
    }

    @Throws(BadPaddingException::class)
    private fun descifrar(textoCifradoYEncodado: String, llaveEnString: String): String {
        //println("Voy a descifrar $textoCifradoYEncodado")
        val cipher = Cipher.getInstance(type)
        cipher.init(Cipher.DECRYPT_MODE, getKey(llaveEnString))
        val textCifradoYDencodado = Base64.getUrlDecoder().decode(textoCifradoYEncodado)
        //println("Texto cifrado $textCifradoYDencodado")
        val textDescifradoYDesencodado = String(cipher.doFinal(textCifradoYDencodado))
        //println("Texto cifrado y desencodado $textDescifradoYDesencodado")
        return textDescifradoYDesencodado
    }

    private fun getKey(llaveEnString: String): SecretKeySpec {
        var llaveUtf8 = llaveEnString.toByteArray(Charsets.UTF_8)
        val sha = MessageDigest.getInstance("SHA-1")
        llaveUtf8 = sha.digest(llaveUtf8)
        llaveUtf8 = llaveUtf8.copyOf(16)
        return SecretKeySpec(llaveUtf8, "AES")
    }
}