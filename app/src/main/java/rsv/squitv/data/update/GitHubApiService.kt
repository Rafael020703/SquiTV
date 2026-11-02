package rsv.squitv.data.update

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String = GitHubUpdateConfig.OWNER,
        @Path("repo") repo: String = GitHubUpdateConfig.PRIMARY_REPO
    ): GitHubReleaseResponse

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String = GitHubUpdateConfig.OWNER,
        @Path("repo") repo: String = GitHubUpdateConfig.PRIMARY_REPO,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GitHubReleaseResponse>
}
