package com.SmartEvCharger.SmartEvCharger.service;

import com.SmartEvCharger.SmartEvCharger.model.Location;
import com.SmartEvCharger.SmartEvCharger.repo.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
public class LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationService.class);

    @Autowired
    private LocationRepository locationRepository;

    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    public Location addLocation(Location location) {
        return locationRepository.save(location);
    }

    public Location getLocationById(Long id) {
        return locationRepository.findById(id).orElse(null);
    }

    public void deleteLocation(Long id) {
        locationRepository.deleteById(id);
    }

    public List<Location> findNearestCharger(double latitude, double longitude, String provider) {
        List<Location> allLocations = locationRepository.findAll();
        if (allLocations == null || allLocations.isEmpty()) {
            logger.warn("No locations found in the database.");
            return Collections.emptyList();
        }

        provider = provider.trim().toLowerCase();
        logger.info("Searching for nearest charger for provider: {}", provider);

        Map<Location, List<Location>> graph = buildGraph(allLocations);
        Location startLocation = findClosestNode(latitude, longitude, allLocations);

        if (startLocation == null) {
            logger.warn("No valid starting location found.");
            return Collections.emptyList();
        }

        Map<Location, Double> distances = new HashMap<>();
        Map<Location, Location> previous = new HashMap<>();
        PriorityQueue<Location> queue = new PriorityQueue<>(Comparator.comparingDouble(distances::get));

        for (Location loc : allLocations) {
            distances.put(loc, Double.MAX_VALUE);
            previous.put(loc, null);
        }
        distances.put(startLocation, 0.0);
        queue.add(startLocation);

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            for (Location neighbor : graph.getOrDefault(current, new ArrayList<>())) {
                double newDist = distances.get(current) + haversineDistance(current.getLatitude(), current.getLongitude(), neighbor.getLatitude(), neighbor.getLongitude());
                if (newDist < distances.get(neighbor)) {
                    distances.put(neighbor, newDist);
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        List<Location> nearestChargers = new ArrayList<>();
        for (Location loc : allLocations) {
            if (loc.getProvider().trim().toLowerCase().contains(provider)) {
                nearestChargers.add(loc);
            }
        }

        nearestChargers.sort(Comparator.comparingDouble(distances::get));

        if (nearestChargers.isEmpty()) {
            logger.info("No charger found for provider: {}", provider);
        }
        return nearestChargers;
    }

    private Map<Location, List<Location>> buildGraph(List<Location> locations) {
        Map<Location, List<Location>> graph = new HashMap<>();
        for (Location loc : locations) {
            graph.putIfAbsent(loc, new ArrayList<>());
            for (Location neighbor : locations) {
                if (!loc.equals(neighbor) && haversineDistance(loc.getLatitude(), loc.getLongitude(), neighbor.getLatitude(), neighbor.getLongitude()) < 10) {
                    graph.get(loc).add(neighbor);
                }
            }
        }
        return graph;
    }

    private Location findClosestNode(double latitude, double longitude, List<Location> locations) {
        return locations.stream().min(Comparator.comparingDouble(loc -> haversineDistance(latitude, longitude, loc.getLatitude(), loc.getLongitude()))).orElse(null);
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
