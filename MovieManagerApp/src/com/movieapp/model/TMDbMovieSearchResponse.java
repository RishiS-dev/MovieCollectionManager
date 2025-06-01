package com.movieapp.model;

import java.util.List;

public class TMDbMovieSearchResponse {
    private List<TMDbRawMovie> results;
    public List<TMDbRawMovie> getResults() {
        return results;
    }
}
