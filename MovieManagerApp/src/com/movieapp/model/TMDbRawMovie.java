package com.movieapp.model;

public class TMDbRawMovie {
    private String title;
    private String overview;
    private String poster_path;
    private double vote_average;
    private int id; // Needed to get trailer link

    public String getTitle() { return title; }
    public String getOverview() { return overview; }
    public String getPoster_path() { return poster_path; }
    public double getVote_average() { return vote_average; }
    public int getId() { return id; }
}
