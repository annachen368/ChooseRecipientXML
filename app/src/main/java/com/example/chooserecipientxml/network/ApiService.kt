package com.example.chooserecipientxml.network

import com.example.chooserecipientxml.model.Contact
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("api/recipients")
    suspend fun getRecipients(
        @Query("start") start: Int,
        @Query("limit") limit: Int
    ): Response<List<Contact>>

    companion object {
//        private const val BASE_URL = "http://10.0.2.2:8080" // Emulator localhost
        private const val BASE_URL = "http://10.0.0.91:8080" // Replace with actual API URL
        /*
        In terminal:
        ifconfig | grep "inet "

        inet 127.0.0.1 netmask 0xff000000
        inet 10.0.0.91 netmask 0xffffff00 broadcast 10.0.0.255
         */

        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}
