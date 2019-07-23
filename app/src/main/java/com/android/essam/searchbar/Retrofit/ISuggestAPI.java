package com.android.essam.searchbar.Retrofit;


import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ISuggestAPI {

    @GET("complete/search")
    Observable<String> getSuggestFromGoogle (@Query("q")String query,
                                             @Query("client")String client);
}
