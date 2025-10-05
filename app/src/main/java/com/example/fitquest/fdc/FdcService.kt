package com.example.fitquest.fdc

import retrofit2.http.Query
import retrofit2.http.GET
import retrofit2.http.Path

interface FdcService {
    @GET("v1/foods/search")
    suspend fun searchFoods(
        @Query("query") query: String,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 50
    ): FdcModels.FdcSearchResponse

    @GET("v1/food/{id}")
    suspend fun getFood(@Path("id") id: Long): FdcModels.FdcFoodDetail
}