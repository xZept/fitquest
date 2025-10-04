package com.example.fitquest.fdc

import retrofit2.http.Query
import retrofit2.http.GET
import retrofit2.http.Path

interface FdcService {
    @GET("v1/foods/search")
    suspend fun searchFoods(
        @Query("query") query: String,
        @Query("dataType") dataType: List<String>? = listOf("Foundation", "SR Legacy"),
        @Query("pageSize") pageSize: Int = 25,
        @Query("pageNumber") pageNumber: Int = 1
    ): FdcModels.FdcSearchResponse

    @GET("v1/food/{fdcId}")
    suspend fun getFood(@Path("fdcId") id: Long): FdcModels.FdcFoodDetail
}