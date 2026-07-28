package com.example.service

import com.example.data.models.Profile
import com.example.utils.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthService {

    private val api = SupabaseClient.api

    private fun getErrorMessage(e: Exception): String {
        return when {
            e.message?.contains("Unable to resolve host") == true ->
                "Sin conexión a internet"

            e.message?.contains("401") == true ->
                "Credenciales incorrectas"

            e.message?.contains("404") == true ->
                "Endpoint no encontrado. Revisa la URL de Supabase"

            e.message?.contains("500") == true ->
                "Error interno del servidor Supabase"

            else ->
                e.message ?: "Error desconocido"
        }
    }


    suspend fun login(
        phone: String,
        pass: String
    ): Result<Profile> = withContext(Dispatchers.IO) {

        try {

            val cleanPhone = Validators.cleanPhoneNumber(phone)
            val email = "$cleanPhone@barberia.rodriguez"


            val existingProfiles = try {

                api.getProfileByPhone("eq.$cleanPhone")

            } catch (e: Exception) {

                throw Exception(
                    "Error buscando usuario: ${getErrorMessage(e)}"
                )
            }


            try {

                val authRes = api.login(
                    mapOf(
                        "email" to email,
                        "password" to pass
                    )
                )


                if (authRes.access_token == null || authRes.user == null) {

                    return@withContext Result.failure(
                        Exception("Supabase no devolvió sesión")
                    )

                }


                SupabaseClient.currentAuthToken =
                    authRes.access_token


                val profile = try {

                    api.getProfileById(
                        "eq.${authRes.user.id}"
                    ).firstOrNull()

                } catch (e: Exception) {

                    throw Exception(
                        "Usuario autenticado pero error cargando perfil: ${getErrorMessage(e)}"
                    )
                }


                return@withContext Result.success(
                    profile ?: Profile(
                        id = authRes.user.id,
                        phone = cleanPhone,
                        fullName = "Cliente",
                        role = "client"
                    )
                )


            } catch (e: Exception) {


                if (existingProfiles.isNotEmpty()) {

                    val profile = existingProfiles.first()


                    return@withContext Result.failure(
                        Exception(
                            "Existe el usuario pero falló autenticación Supabase: ${getErrorMessage(e)}"
                        )
                    )

                }


                return@withContext Result.failure(
                    Exception(
                        "Login falló: ${getErrorMessage(e)}"
                    )
                )

            }


        } catch (e: Exception) {

            Result.failure(
                Exception(
                    "Error login: ${getErrorMessage(e)}"
                )
            )
        }
    }



    suspend fun register(
        phone: String,
        fullName: String,
        pass: String
    ): Result<Profile> = withContext(Dispatchers.IO) {


        try {

            val cleanPhone =
                Validators.cleanPhoneNumber(phone)


            val existing = try {

                api.getProfileByPhone(
                    "eq.$cleanPhone"
                )

            } catch (e: Exception) {

                throw Exception(
                    "Error comprobando teléfono: ${getErrorMessage(e)}"
                )
            }



            if(existing.isNotEmpty()) {

                return@withContext Result.failure(
                    Exception(
                        "Este teléfono ya está registrado"
                    )
                )

            }



            val email =
                "$cleanPhone@barberia.rodriguez"


            var userId =
                UUID.randomUUID().toString()



            val authRes = try {


                api.signup(
                    mapOf(
                        "email" to email,
                        "password" to pass,
                        "data" to mapOf(
                            "phone" to cleanPhone,
                            "full_name" to fullName,
                            "role" to "client"
                        )
                    )
                )


            } catch (e: Exception) {


                throw Exception(
                    "Error creando usuario Supabase: ${getErrorMessage(e)}"
                )

            }



            if(authRes.user != null) {

                userId = authRes.user.id

            }


            if(authRes.access_token != null) {

                SupabaseClient.currentAuthToken =
                    authRes.access_token

            }



            val profile = Profile(
                id = userId,
                phone = cleanPhone,
                fullName = fullName,
                role = "client"
            )



            val created = try {

                api.createProfile(profile)

            } catch(e: Exception) {


                throw Exception(
                    "Usuario creado pero error guardando perfil: ${getErrorMessage(e)}"
                )

            }



            Result.success(
                created.firstOrNull() ?: profile
            )



        } catch(e: Exception) {


            Result.failure(
                Exception(
                    "Registro falló: ${getErrorMessage(e)}"
                )
            )

        }

    }



    suspend fun updatePhone(
        userId: String,
        newPhone: String
    ): Result<Profile> = withContext(Dispatchers.IO) {


        try {


            val cleanPhone =
                Validators.cleanPhoneNumber(newPhone)



            val updated = api.updateProfile(
                "eq.$userId",
                mapOf(
                    "phone" to cleanPhone
                )
            )



            if(updated.isNotEmpty()) {

                Result.success(
                    updated.first()
                )

            } else {

                Result.failure(
                    Exception(
                        "Supabase no actualizó ningún registro"
                    )
                )

            }



        } catch(e: Exception) {


            Result.failure(
                Exception(
                    "Error actualizando teléfono: ${getErrorMessage(e)}"
                )
            )

        }

    }
}