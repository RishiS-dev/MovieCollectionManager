package com.movieapp.ui;

import com.movieapp.api.TMDbApiService;
import com.movieapp.model.Movie;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.net.URISyntaxException;
import java.awt.Desktop;
import java.io.InputStream;
import java.net.MalformedURLException;

public class MovieListPanel extends JPanel {

    private TMDbApiService apiService;
    private JTextField searchField;
    private JComboBox<String> filterBox;
    private JComboBox<String> sortBox;
    private JPanel movieGridPanel;
    private JScrollPane scrollPane;
    private List<Movie> currentMovies; // Keeps the last search results

    public MovieListPanel() {
        this.apiService = new TMDbApiService();

        setLayout(new BorderLayout());
        setupTopPanel();
        setupMovieGrid();
    }

    private void setupTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        searchField = new JTextField(30);
        JButton searchButton = new JButton("Search");

        filterBox = new JComboBox<>(new String[]{"All", "High Rating (>7)", "Low Rating (<=7)"});
        sortBox = new JComboBox<>(new String[]{"Sort by Title (A-Z)", "Sort by Title (Z-A)", "Sort by Rating (High-Low)", "Sort by Rating (Low-High)"});

        searchButton.addActionListener(this::onSearch);

        filterBox.addActionListener(e -> refreshMovieGrid());
        sortBox.addActionListener(e -> refreshMovieGrid());

        topPanel.add(new JLabel("Search:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(new JLabel("Filter:"));
        topPanel.add(filterBox);
        topPanel.add(new JLabel("Sort:"));
        topPanel.add(sortBox);

        add(topPanel, BorderLayout.NORTH);
    }

    private void setupMovieGrid() {
        movieGridPanel = new JPanel();
        movieGridPanel.setLayout(new BoxLayout(movieGridPanel, BoxLayout.X_AXIS));
        movieGridPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        scrollPane = new JScrollPane(movieGridPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void onSearch(ActionEvent e) {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a search query.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            movieGridPanel.removeAll();
            try {
                currentMovies = apiService.searchMoviesParsed(query);
                refreshMovieGrid();
            } catch (IOException | InterruptedException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error fetching movies: " + ex.getMessage());
            }
        });
    }

    private void refreshMovieGrid() {
        if (currentMovies == null) return;

        List<Movie> filtered = applyFilter(currentMovies);
        List<Movie> sorted = applySort(filtered);

        movieGridPanel.removeAll();

        if (sorted.isEmpty()) {
            JPanel centeredMessagePanel = new JPanel(new GridBagLayout());
            centeredMessagePanel.add(new JLabel("No results found."));
            movieGridPanel.add(centeredMessagePanel);
        } else {
            boolean firstCard = true;
            for (Movie movie : sorted) {
                if (!firstCard) {
                    movieGridPanel.add(Box.createHorizontalStrut(15));
                }
                movieGridPanel.add(createMovieCard(movie));
                firstCard = false;
            }
        }

        movieGridPanel.revalidate();
        movieGridPanel.repaint();
    }

    private List<Movie> applyFilter(List<Movie> movies) {
        String selectedFilter = (String) filterBox.getSelectedItem();
        if ("High Rating (>7)".equals(selectedFilter)) {
            return movies.stream().filter(m -> m.getRating() > 7).collect(Collectors.toList());
        } else if ("Low Rating (<=7)".equals(selectedFilter)) {
            return movies.stream().filter(m -> m.getRating() <= 7).collect(Collectors.toList());
        }
        return movies;
    }

    private List<Movie> applySort(List<Movie> movies) {
        String selectedSort = (String) sortBox.getSelectedItem();
        switch (selectedSort) {
            case "Sort by Title (A-Z)":
                return movies.stream().sorted(Comparator.comparing(Movie::getTitle)).collect(Collectors.toList());
            case "Sort by Title (Z-A)":
                return movies.stream().sorted(Comparator.comparing(Movie::getTitle).reversed()).collect(Collectors.toList());
            case "Sort by Rating (High-Low)":
                return movies.stream().sorted(Comparator.comparing(Movie::getRating).reversed()).collect(Collectors.toList());
            case "Sort by Rating (Low-High)":
                return movies.stream().sorted(Comparator.comparing(Movie::getRating)).collect(Collectors.toList());
            default:
                return movies;
        }
    }

    private JPanel createMovieCard(Movie movie) {
        JPanel card = new JPanel(new BorderLayout(5, 5)); 
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10) 
        ));
        card.setPreferredSize(new Dimension(600, 520));
        card.setMinimumSize(new Dimension(400, 480));
        card.setMaximumSize(new Dimension(700, 600));

        // Poster
        JLabel posterLabel = new JLabel();
        posterLabel.setHorizontalAlignment(JLabel.CENTER);
        posterLabel.setPreferredSize(new Dimension(230, 300));
        
        Image img = null;
        String imagePath = movie.getPosterPath();

        try {
            if (imagePath != null && !imagePath.trim().isEmpty()) {
                if (imagePath.startsWith("https://via.placeholder.com")) {
                    // Try to load placeholder directly as it's a different domain
                    try {
                        img = ImageIO.read(new URL(imagePath));
                    } catch (IOException e) {
                        System.err.println("Error loading placeholder image directly: " + imagePath + " - " + e.getMessage());
                        // img remains null, fallback will apply
                    }
                } else {
                    // Fetch regular TMDb image using our service with SSL context and retries
                    try (InputStream imageStream = apiService.getImageStream(imagePath)) {
                        if (imageStream != null) {
                            img = ImageIO.read(imageStream);
                        }
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Error fetching image via service for URL: " + imagePath + " - " + e.getMessage());
                        // img remains null, fallback will apply
                    }
                }
            }

            if (img != null) {
                // Scale image while maintaining aspect ratio
                int originalWidth = img.getWidth(null);
                int originalHeight = img.getHeight(null);
                int boundWidth = 230;
                int boundHeight = 300;
                int newWidth = originalWidth;
                int newHeight = originalHeight;

                if (originalWidth > boundWidth) {
                    newWidth = boundWidth;
                    newHeight = (newWidth * originalHeight) / originalWidth;
                }
                if (newHeight > boundHeight) {
                    newHeight = boundHeight;
                    newWidth = (newHeight * originalWidth) / originalHeight;
                }
                Image scaledImg = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                posterLabel.setIcon(new ImageIcon(scaledImg));
            } else {
                // Fallback text if img is still null after all attempts
                if (imagePath == null || imagePath.trim().isEmpty() || imagePath.endsWith("No+Image")) {
                    posterLabel.setText("No Image Available");
                } else {
                    posterLabel.setText("Image Load Error");
                }
            }
        } catch (Exception e) { // Catch any other unexpected errors during image processing
            posterLabel.setText("Image Process Error");
            System.err.println("Unexpected error processing image: " + imagePath + " - " + e.getMessage());
            e.printStackTrace();
        }
        card.add(posterLabel, BorderLayout.NORTH);

        // Info Panel (Title, Rating, Genres, Overview)
        JPanel textInfoPanel = new JPanel();
        textInfoPanel.setLayout(new BoxLayout(textInfoPanel, BoxLayout.Y_AXIS));
        textInfoPanel.setBackground(Color.WHITE); // Ensure readability

        JLabel titleLabel = new JLabel("<html><b>" + movie.getTitle() + "</b></html>");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel ratingLabel = new JLabel("Rating: " + movie.getRating());
        ratingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String genresText = "Genres: N/A";
        if (movie.getGenres() != null && movie.getGenres().length > 0) {
            genresText = "Genres: " + String.join(", ", movie.getGenres());
        }
        JLabel genresLabel = new JLabel(genresText);
        genresLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JTextArea overviewArea = new JTextArea(movie.getOverview());
        overviewArea.setWrapStyleWord(true);
        overviewArea.setLineWrap(true);
        overviewArea.setEditable(false);
        overviewArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        overviewArea.setOpaque(false);
        overviewArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        overviewArea.setRows(6);

        textInfoPanel.add(titleLabel);
        textInfoPanel.add(ratingLabel);
        textInfoPanel.add(genresLabel);
        textInfoPanel.add(Box.createRigidArea(new Dimension(0, 5))); // Spacing
        textInfoPanel.add(new JLabel("Overview:"));
        textInfoPanel.add(overviewArea); // Add overview here
        
        card.add(textInfoPanel, BorderLayout.CENTER);

        // Trailer Button
        if (movie.getTrailerLink() != null && !movie.getTrailerLink().isEmpty()) {
            JButton trailerButton = new JButton("Watch Trailer");
            trailerButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            trailerButton.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URL(movie.getTrailerLink()).toURI());
                } catch (IOException | URISyntaxException ex) {
                    JOptionPane.showMessageDialog(card, "Could not open trailer: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            });
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Panel to center the button
            buttonPanel.add(trailerButton);
            card.add(buttonPanel, BorderLayout.SOUTH);
        }

        return card;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Movie Explorer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 600);
            frame.setLocationRelativeTo(null);
            frame.add(new MovieListPanel());
            frame.setVisible(true);
        });
    }
}
