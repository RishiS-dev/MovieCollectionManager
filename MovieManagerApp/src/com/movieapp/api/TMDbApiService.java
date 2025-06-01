package com.movieapp.api;

import com.google.gson.*;
import com.movieapp.model.Movie;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.InputStream;

public class TMDbApiService {
    private static final String API_KEY = "024502a95388af12c30fb52331ee3760";
    private static final String API_BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
    
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000; // Start with 1 second delay
    private static final String USER_AGENT = "MovieManagerApp/1.0"; // Added User-Agent

    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public TMDbApiService() {
        // Enable all TLS protocols and configure SSL
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3");
        
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30));
        
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            
            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // Configure the HTTP client with our SSL context
            builder.sslContext(sslContext);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            System.err.println("Failed to initialize SSL context: " + e.getMessage());
        }
        
        httpClient = builder.build();
    }

    // Generic method to execute HTTP requests with retries
    private <T> T executeWithRetry(URI uri, HttpRequest.Builder requestBuilder, 
                                   HttpResponse.BodyHandler<T> responseHandler) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder
                .uri(uri)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        IOException lastException = null;
        
        // Implement retry with exponential backoff
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Add some delay between retries (except first attempt)
                if (attempt > 0) {
                    int delayMs = RETRY_DELAY_MS * (1 << (attempt - 1)); // Exponential backoff
                    Thread.sleep(delayMs);
                    System.out.println("Retrying request, attempt " + (attempt + 1) + " of " + MAX_RETRIES);
                }
                
                HttpResponse<T> response = httpClient.send(request, responseHandler);
                
                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    throw new IOException("API call failed with HTTP code: " + response.statusCode());
                }
            } catch (IOException e) {
                lastException = e;
                System.err.println("Request failed (attempt " + (attempt + 1) + "): " + e.getMessage());
                
                // If this is not a network/SSL error, don't retry
                if (!(e instanceof SSLHandshakeException) && 
                    !(e.getCause() instanceof SSLHandshakeException) &&
                    !(e instanceof java.net.ConnectException) &&
                    !(e instanceof java.net.SocketTimeoutException)) {
                    throw e;
                }
            }
        }
        
        // If we get here, all retries failed
        throw new IOException("All retry attempts failed: " + (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }

    // Search movies
    public List<Movie> searchMoviesParsed(String query) throws IOException, InterruptedException {
        String url = API_BASE_URL + "/search/movie?api_key=" + API_KEY + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        
        try {
            String responseBody = executeWithRetry(
                URI.create(url), 
                HttpRequest.newBuilder(),
                HttpResponse.BodyHandlers.ofString()
            );
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray results = json.getAsJsonArray("results");

            List<Movie> movies = new ArrayList<>();
            for (JsonElement element : results) {
                JsonObject movieJson = element.getAsJsonObject();
                int id = movieJson.get("id").getAsInt();
                String title = movieJson.get("title").getAsString();
                String overview = movieJson.get("overview").getAsString();
                String posterPath = movieJson.has("poster_path") && !movieJson.get("poster_path").isJsonNull()
                        ? IMAGE_BASE_URL + movieJson.get("poster_path").getAsString()
                        : "https://via.placeholder.com/300x450?text=No+Image";
                double rating = movieJson.get("vote_average").getAsDouble();

                // Fetch trailer link and genres for each movie
                String trailerLink = null;
                String[] genres = new String[0];
                try {
                    trailerLink = getTrailerLink(id);
                    genres = getGenres(id);
                } catch (IOException | InterruptedException e) {
                    // Log or handle the error for individual movie detail fetching
                    System.err.println("Error fetching details for movie ID " + id + ": " + e.getMessage());
                    // Continue processing other movies
                }

                Movie movie = new Movie(title, overview, posterPath, rating, trailerLink, genres);
                movie.setId(id);
                movies.add(movie);
            }
            return movies;
        } catch (Exception e) {
            throw new IOException("Failed to search movies: " + e.getMessage(), e);
        }
    }

    // Get Trailer Link
    public String getTrailerLink(int movieId) throws IOException, InterruptedException {
        String url = API_BASE_URL + "/movie/" + movieId + "/videos?api_key=" + API_KEY;
        
        try {
            String responseBody = executeWithRetry(
                URI.create(url),
                HttpRequest.newBuilder(),
                HttpResponse.BodyHandlers.ofString()
            );
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray results = json.getAsJsonArray("results");

            for (JsonElement element : results) {
                JsonObject videoJson = element.getAsJsonObject();
                String type = videoJson.get("type").getAsString();
                String site = videoJson.get("site").getAsString();
                if (type.equalsIgnoreCase("Trailer") && site.equalsIgnoreCase("YouTube")) {
                    String key = videoJson.get("key").getAsString();
                    return "https://www.youtube.com/watch?v=" + key;
                }
            }
            return null;
        } catch (Exception e) {
            throw new IOException("Failed to get trailer: " + e.getMessage(), e);
        }
    }

    // Get Genres names
    public String[] getGenres(int movieId) throws IOException, InterruptedException {
        String url = API_BASE_URL + "/movie/" + movieId + "?api_key=" + API_KEY;
        
        try {
            String responseBody = executeWithRetry(
                URI.create(url),
                HttpRequest.newBuilder(),
                HttpResponse.BodyHandlers.ofString()
            );
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray genresArray = json.getAsJsonArray("genres");

            List<String> genres = new ArrayList<>();
            for (JsonElement element : genresArray) {
                JsonObject genreObj = element.getAsJsonObject();
                String name = genreObj.get("name").getAsString();
                genres.add(name);
            }
            return genres.toArray(new String[0]);
        } catch (Exception e) {
            throw new IOException("Failed to get genres: " + e.getMessage(), e);
        }
    }

    // New method to fetch an image as an InputStream
    public InputStream getImageStream(String imageUrl) throws IOException, InterruptedException {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IOException("Image URL cannot be null or empty.");
        }
        // For image requests, we might not need/want "Accept: application/json"
        // We can pass a request builder that doesn't set it, or rely on User-Agent only from executeWithRetry
        HttpRequest.Builder imageRequestBuilder = HttpRequest.newBuilder();
        // The executeWithRetry will add the User-Agent.
        // We don't need an "Accept: application/json" header for images.
        // Let's refine executeWithRetry or this call slightly.

        // Simplification: executeWithRetry adds User-Agent. Other headers are fine.
        // The server should ignore "Accept: application/json" for an image URL.
        return executeWithRetry(URI.create(imageUrl), imageRequestBuilder, HttpResponse.BodyHandlers.ofInputStream());
    }
}