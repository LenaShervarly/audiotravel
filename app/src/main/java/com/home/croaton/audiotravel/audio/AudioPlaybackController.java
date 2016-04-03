package com.home.croaton.audiotravel.audio;

import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import com.home.croaton.audiotravel.R;
import com.home.croaton.audiotravel.domain.AudioPoint;
import com.home.croaton.audiotravel.domain.Point;
import com.home.croaton.audiotravel.domain.Route;
import com.home.croaton.audiotravel.domain.RouteSerializer;
import com.home.croaton.audiotravel.location.LocationHelper;
import com.home.croaton.audiotravel.maps.Circle;

import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.util.GeoPoint;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class AudioPlaybackController {
    private Route _route;
    private String _routeFileName;

    public AudioPlaybackController(Context context, String excursionName) {
        if (excursionName.equals("Gamlastan")) {
            _routeFileName = "Demo";
            deserializeFromFileOrResource(context, R.raw.demo);
        }
        if (excursionName.equals("Abrahamsberg")) {
            _routeFileName = "Abrahamsberg";
            deserializeFromFileOrResource(context, R.raw.abrahamsberg);
        } else {
            throw new IllegalArgumentException("Unsupported excursion");

        }
    }

    public static void stopAnyPlayback(Context context) {
        context.stopService(new Intent(context, AudioService.class));
    }

    private void deserializeFromFileOrResource(Context context, int resId) {
        if (fileExists(context, _routeFileName)) {
            try {
                _route = RouteSerializer.deserializeFromFile(context.openFileInput(_routeFileName));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else
            _route = RouteSerializer.deserializeFromResource(context.getResources(), resId);
    }

    public boolean fileExists(Context context, String fileName) {
        return context.getFileStreamPath(fileName).exists();
    }

    public Pair<Integer, ArrayList<String>> getResourceToPlay(GeoPoint position) {
        return getResourceToPlay(position, false);
    }

    // ToDo: according to user choose files
    public Pair<Integer, ArrayList<String>> getResourceToPlay(GeoPoint position, boolean ignoreDone) {
        float min = Integer.MAX_VALUE;
        AudioPoint closestPoint = null;

        for (AudioPoint point : _route.audioPoints()) {
            if (!ignoreDone && _route.isAudioPointPassed(point.Number))
                continue;

            float distance = LocationHelper.GetDistance(position, point.Position);
            if (distance < min && distance <= point.Radius) {
                min = distance;
                closestPoint = point;
            }
        }

        if (closestPoint == null)
            return null;

        return new Pair<>(closestPoint.Number, _route.getAudiosForPoint(closestPoint));
    }

    public void markAudioPoint(int pointNumber, boolean passed) {
        _route.markAudioPoint(pointNumber, passed);
    }

    public ArrayList<Point> geoPoints() {
        return _route.geoPoints();
    }

    public ArrayList<AudioPoint> audioPoints() {
        return _route.audioPoints();
    }

    public boolean[] getDoneArray() {
        ArrayList<AudioPoint> audioPoints = _route.audioPoints();
        boolean[] doneIndicators = new boolean[audioPoints.size()];

        for (int i = 0; i < audioPoints.size(); i++)
            doneIndicators[i] = _route.isAudioPointPassed(i);

        return doneIndicators;
    }

    public void specialSaveRouteToDisc(ArrayList<Circle> circles, ArrayList<Marker> pointMarkers,
                                       Context context) {
        if (circles.size() > 0 && pointMarkers.size() > 0)
            _route.updateAudioPoints(circles, pointMarkers);

        FileOutputStream fs = null;
        try {
            fs = context.openFileOutput(_routeFileName, Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        RouteSerializer.serialize(_route, fs);
    }

    public void startPlaying(Context context, ArrayList<String> audioToPlay) {
        Intent startingIntent = new Intent(context, AudioService.class);
        startingIntent.putExtra(AudioService.Command, AudioServiceCommand.LoadTracks);
        startingIntent.putExtra(AudioService.NewTracks, audioToPlay);

        context.startService(startingIntent);
    }

    public boolean isAudioPointPassed(Integer number) {
        return _route.isAudioPointPassed(number);
    }

    public AudioPoint getFirstNotDoneAudioPoint() {
        for (AudioPoint audioPoint : _route.audioPoints()) {
            if (!_route.isAudioPointPassed(audioPoint.Number))
                return (AudioPoint) audioPoint.clone();
        }

        return null;
    }
}
