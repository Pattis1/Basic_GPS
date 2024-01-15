package com.example.gpsodev;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import android.location.Geocoder;
import com.google.android.gms.maps.model.Marker;
import java.util.Locale;
import android.location.Address;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.TravelMode;
import android.util.Log;




public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Marker currentLocationMarker;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragmentContainer);

        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getSupportFragmentManager().beginTransaction().replace(R.id.mapFragmentContainer, mapFragment, "mapFragment").commit();
        }

        mapFragment.getMapAsync(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        requestLocationPermission();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        googleMap.getUiSettings().setZoomGesturesEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        googleMap.setOnMapClickListener(this);

        getCurrentLocation();
    }

    @Override
    public void onMapClick(LatLng latLng) {
        getAddressFromCoordinates(latLng.latitude, latLng.longitude);

        if (googleMap != null) {
            if (currentLocationMarker != null) {
                currentLocationMarker.remove();
            }

            currentLocationMarker = googleMap.addMarker(new MarkerOptions().position(latLng).title("Clicked Location"));
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }

    private void getAddressFromCoordinates(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);


                showInfoDialog(latitude, longitude, address);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showInfoDialog(double latitude, double longitude, Address address) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.custom_info_dialog, null);

        TextView textViewAddress = dialogView.findViewById(R.id.textViewAddress);
        ImageView locationImageView = dialogView.findViewById(R.id.locationImageView);

        textViewAddress.setText(getString(R.string.address_label, address.getAddressLine(0)));

        String streetViewImageUrl = getStreetViewImageUrl(latitude, longitude);
        new DownloadImageTask(locationImageView).execute(streetViewImageUrl);

        showTravelInfoDialog(dialogView, latitude, longitude);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Tamam", null);

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showTravelInfoDialog(View dialogView, double destinationLatitude, double destinationLongitude) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            double originLatitude = location.getLatitude();
                            double originLongitude = location.getLongitude();

                            StringBuilder travelInfoBuilder = new StringBuilder();

                            travelInfoBuilder.append(getTravelInfo(originLatitude, originLongitude, destinationLatitude, destinationLongitude, "walking", "Yürüme"));
                            travelInfoBuilder.append("\n\n");
                            travelInfoBuilder.append(getTravelInfo(originLatitude, originLongitude, destinationLatitude, destinationLongitude, "driving", "Araba"));
                            travelInfoBuilder.append("\n\n");
                            travelInfoBuilder.append(getTravelInfo(originLatitude, originLongitude, destinationLatitude, destinationLongitude, "transit", "Toplu Taşıma"));


                            TextView textViewTravelInfo = dialogView.findViewById(R.id.textViewTravelInfo);
                            textViewTravelInfo.setText(travelInfoBuilder.toString());
                        }
                    })
                    .addOnFailureListener(this, e -> {
                    });
        } else {
            requestLocationPermission();
        }
    }

    private String getTravelInfo(double originLatitude, double originLongitude, double destinationLatitude, double destinationLongitude, String mode, String modeDisplayName) {
        String apiKey = "AIzaSyC2hMjnREEzHzSvhhkRWq8BX2982kFcdg0";
        GeoApiContext geoApiContext = new GeoApiContext.Builder().apiKey(apiKey).build();

        DirectionsResult result;
        try {
            result = DirectionsApi.newRequest(geoApiContext)
                    .origin(new com.google.maps.model.LatLng(originLatitude, originLongitude))
                    .destination(new com.google.maps.model.LatLng(destinationLatitude, destinationLongitude))
                    .mode(TravelMode.valueOf(mode.toUpperCase()))
                    .await();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("DirectionsAPI", "Mod için seyahat bilgisi alınamadı: " + mode, e);
            return "";
        }

        if (result != null && result.routes != null && result.routes.length > 0) {
            return parseTravelInfo(modeDisplayName, result);
        } else {
            return "";
        }
    }

    private String parseTravelInfo(String modeDisplayName, DirectionsResult result) {
        if (result.routes != null && result.routes.length > 0) {
            DirectionsRoute route = result.routes[0];
            DirectionsLeg leg = route.legs[0];

            String durationText = leg.duration.humanReadable;
            String distanceText = leg.distance.humanReadable;

            return modeDisplayName + " için Seyahat Bilgisi:\nSüre: " + durationText + "\nMesafe: " + distanceText;
        } else {
            return "";
        }
    }

    private class DownloadDirectionsTask extends AsyncTask<String, Void, String> {
        View dialogView;
        String mode;

        @Override
        protected String doInBackground(String... urls) {
            return "{ \"status\": \"OK\", \"routes\": [ {\"legs\": [ {\"duration\": { \"text\": \"10 mins\", \"value\": 600 }, \"distance\": { \"text\": \"2 km\", \"value\": 2000 } } ] } ] }";
        }

        @Override
        protected void onPostExecute(String result) {
            parseDirectionsResponse(dialogView, mode, result);
        }
    }

    private void parseDirectionsResponse(View dialogView, String mode, Object response) {
        if (response instanceof String) {
            parseJsonDirectionsResponse(dialogView, mode, (String) response);
        } else if (response instanceof DirectionsResult) {
            parseDirectionsResult(dialogView, mode, (DirectionsResult) response);
        }
    }

    private void parseJsonDirectionsResponse(View dialogView, String mode, String jsonResponse) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            if (json.has("status") && json.getString("status").equals("OK")) {
                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    JSONArray legs = route.getJSONArray("legs");
                    if (legs.length() > 0) {
                        JSONObject leg = legs.getJSONObject(0);
                        JSONObject duration = leg.getJSONObject("duration");
                        JSONObject distance = leg.getJSONObject("distance");

                        String durationText = duration.getString("text");
                        String distanceText = distance.getString("text");

                        TextView textViewTravelInfo = dialogView.findViewById(R.id.textViewTravelInfo);
                        String travelInfo = "Travel info for " + mode + ":\nDuration: " + durationText + "\nDistance: " + distanceText;
                        textViewTravelInfo.setText(travelInfo);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void parseDirectionsResult(View dialogView, String mode, DirectionsResult result) {
        if (result.routes != null && result.routes.length > 0) {
            DirectionsRoute route = result.routes[0];
            DirectionsLeg leg = route.legs[0];

            String durationText = leg.duration.humanReadable;
            String distanceText = leg.distance.humanReadable;

            TextView textViewTravelInfo = dialogView.findViewById(R.id.textViewTravelInfo);
            String travelInfo = "Travel info for " + mode + ":\nDuration: " + durationText + "\nDistance: " + distanceText;
            textViewTravelInfo.setText(travelInfo);
        }
    }




    private String getStreetViewImageUrl(double latitude, double longitude) {
        String apiKey = "AIzaSyC2hMjnREEzHzSvhhkRWq8BX2982kFcdg0";
        return "https://maps.googleapis.com/maps/api/streetview?size=400x200&location=" + latitude + "," + longitude + "&fov=90&heading=235&pitch=10&key=" + apiKey;
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView imageView;

        public DownloadImageTask(ImageView imageView) {
            this.imageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            Bitmap bitmap = null;

            try {
                InputStream in = new URL(url).openStream();
                bitmap = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (imageView != null) {
                imageView.setImageBitmap(result);
            }
        }
    }



    private void getCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null && googleMap != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();

                            LatLng currentLatLng = new LatLng(latitude, longitude);


                            if (currentLocationMarker == null) {

                                currentLocationMarker = googleMap.addMarker(new MarkerOptions().position(currentLatLng).title("Current Location"));
                            } else {

                                currentLocationMarker.setPosition(currentLatLng);
                            }


                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15.0f));
                        }
                    });
        }
    }

    public void onCheckLocationClick(View view) {
        if (googleMap != null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();

                            getAddressFromCoordinates(latitude, longitude);
                        }
                    });
            getCurrentLocation();
        }
    }


    private void requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
