package br.edu.ufcg.les142;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import br.edu.ufcg.les142.models.Relato;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.parse.*;

import java.util.*;

import static br.edu.ufcg.les142.R.*;

@SuppressWarnings("ALL")
public class InitialActivity extends FragmentActivity implements LocationListener,
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {
    /*
    * Define a request code to send to Google Play services This code is returned in
    * Activity.onActivityResult
    */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    // The update interval
    private static final int UPDATE_INTERVAL_IN_SECONDS = 5;

    /*
    * Constants for handling location results
    */
    // Conversion from feet to meters
    private static final float METERS_PER_FEET = 0.3048f;

    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;

    // Update interval in milliseconds
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
            * UPDATE_INTERVAL_IN_SECONDS;

    // A fast interval ceiling
    private static final int FAST_CEILING_IN_SECONDS = 1;

    // A fast ceiling of update intervals, used when the app is visible
    private static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
            * FAST_CEILING_IN_SECONDS;

    // Initial offset for calculating the map bounds
    private static final double OFFSET_CALCULATION_INIT_DIFF = 1.0;

    // Accuracy for calculating the map bounds
    private static final float OFFSET_CALCULATION_ACCURACY = 0.01f;

    private boolean hasSetUpInitialLocation;

    private Location currentLocation;

    private Location lastLocation;

    private boolean relatoClick = false;

    private Marker markerClicked;

    //Marcadores no mapa
    private final Map<String, Marker> mapMarkers = new HashMap<String, Marker>();

    private String selectedRelatoObjectId;

    // private SupportMapFragment mapa;
    private LocationRequest locationRequest;

    // Stores the current instantiation of the location client in this object
    private LocationClient locationClient;

    // Fields for the map radius in feet
    private float radius = 250.0f;

    private static final int MAX_POST_SEARCH_DISTANCE = 100;

    private static final int MAX_POST_SEARCH_RESULTS = 100;

    private static Geocoder gcd;

    private String id;

    // Map fragment
    private SupportMapFragment mapa;

    private TextView hintTextView;

    /*
     * Initialize the Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(layout.main);
        ParseInstallation installation =  ParseInstallation.getCurrentInstallation();
        installation.put("Relato", true);
        installation.saveInBackground();
        PushService.setDefaultPushCallback(this, InitialActivity.class);
        // Create a new global location parameters object
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setFastestInterval(FAST_INTERVAL_CEILING_IN_MILLISECONDS);

        // Create a new location client, using the enclosing class to handle callbacks.
        locationClient = new LocationClient(this, this, this);

        hintTextView = (TextView) findViewById(R.id.hint);
        hintTextView.setText("Para relatar um problema\r\nna sua localização\r\nclique aqui ▶");
        final long startTime = 12000;
        final long interval = 1000;
        CountDownTimer count = new CountDownTimer(startTime, interval) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                hintTextView = (TextView) findViewById(R.id.hint);
                hintTextView.setText("");
            }
        }.start();
        // Set up the map fragment
        mapa = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        // Enable the current location "blue dot"
        mapa.getMap().setMyLocationEnabled(true);

        mapa.getMap().setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            public void onCameraChange(CameraPosition position) {
                mostraRelatos();
            }
        });
        final ProgressDialog dialogShowRelato = new ProgressDialog(InitialActivity.this);
        dialogShowRelato.setMessage(getString(string.openning_relate));
        mapa.getMap().setOnMarkerClickListener(
                new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker mark) {
                        if (!relatoClick) {
                            relatoClick = true;
                            markerClicked = mark;
                            return false;
                        } else if (relatoClick && mark.equals(markerClicked)) {
                            relatoClick = false;
                        } else if (relatoClick && !mark.equals(markerClicked)) {
                            markerClicked = mark;
                            return false;
                        }
                        String rel_id = "";
                        for (String key : mapMarkers.keySet()) {
                            if (mapMarkers.get(key).getTitle().equals(mark.getTitle()) && mapMarkers.get(key).getPosition().equals(mark.getPosition())) {
                                rel_id = key;
                                id = rel_id;
                            }
                        }
                        dialogShowRelato.show();
                        ParseQuery<Relato> query = Relato.getQuery();
                        query.getInBackground(rel_id, new GetCallback<Relato>() {

                            @Override
                            public void done(Relato relato, ParseException e) {
                                if (e == null) {
                                    relato.pinInBackground();
                                    Intent intent = new Intent(InitialActivity.this, DescRelatoActivity.class);
                                    Bundle bundle = new Bundle();
                                    bundle.putString("desc", relato.getDescricao());
                                    ArrayList<String> apoios = new ArrayList<String>();
                                    for (ParseUser user : relato.getApoios()) {
                                        try {
                                            user.fetchIfNeeded();
                                            apoios.add(user.getUsername());
                                        } catch (ParseException e1) {
                                            e1.printStackTrace();
                                        }


                                    }
                                    bundle.putStringArrayList("apoios", apoios);
                                    bundle.putString("rel_id", id);

                                    if (relato.getStatusRelato() != null) {
                                        bundle.putString("status", relato.getStatusRelato().toString());
                                    } else {
                                        bundle.putString("status", "");
                                    }

                                    if (relato.getTipoRelato() != null) {
                                        bundle.putString("tipo", relato.getTipoRelato().toString());
                                    } else {
                                        bundle.putString("tipo", "");
                                    }

                                    try {
                                        ParseUser parseUser = relato.getUser().fetchIfNeeded();
                                        bundle.putString("author", parseUser.getUsername());
                                    } catch (ParseException e1) {
                                        e1.printStackTrace();
                                    }

                                    if (relato.getImage() != null) {
                                        bundle.putByteArray("image", relato.getImage());

                                    }
                                    intent.putExtras(bundle);
                                    dialogShowRelato.hide();
                                    startActivity(intent);
                                }
                            }
                        });
                        return true;
                    }
                }
        );

        View zoomControls;
        zoomControls = mapa.getView().findViewById(1); //FIXME: Para funcionar tem que desativar os warnings
        if (zoomControls != null && zoomControls.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            // ZoomControl is inside of RelativeLayout
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) zoomControls.getLayoutParams();

            // Align it to - parent top|left
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);

            // Update margins, set to 10dp
            final int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
                    getResources().getDisplayMetrics());
            params.setMargins(margin, margin, margin, margin);
        }




        // Set up the handler for the post button click
        ImageButton relatoButton = (ImageButton) findViewById(R.id.relatarButton);
        relatoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Only allow posts if we have a location
                Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
                if (myLoc == null) {
                    Toast.makeText(InitialActivity.this,
                            "Por favor tente de novo quando sua localização aparecer no mapa.", Toast.LENGTH_LONG).show();
                    return;
                }

                Intent intent = new Intent(InitialActivity.this, PostRelatoActivity.class);
                intent.putExtra(Application.INTENT_EXTRA_LOCATION, myLoc);
                startActivity(intent);
            }
        });
        this.gcd = new Geocoder(getBaseContext(), Locale.getDefault());
    }

    @Override
    protected void onResume() {
        super.onResume();

        //hintTextView = (TextView) findViewById(R.id.hint);
        //hintTextView.setText("");
    }

    private void mostraRelatos() {
        Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
        if (myLoc == null) {
            cleanUpMarkers(new HashSet<String>());
            return;
        }
        final ParseGeoPoint myPoint = geoPointFromLocation(myLoc);
        ParseQuery<Relato> mapQuery = Relato.getQuery();
        mapQuery.whereWithinKilometers("location", myPoint, MAX_POST_SEARCH_DISTANCE);
        mapQuery.include("user");
        mapQuery.orderByDescending("createdAt");
        mapQuery.setLimit(MAX_POST_SEARCH_RESULTS);
        mapQuery.findInBackground(new FindCallback<Relato>() {
            @Override
            public void done(List<Relato> objects, ParseException e) {
                if (e != null) {
                    if (Application.APPDEBUG) {
                        Log.d(Application.APPTAG, "An error occurred while querying for map posts.", e);
                    }
                    return;
                }
                Set<String> toKeep = new HashSet<String>();
                for (Relato relato : objects) {
                    toKeep.add(relato.getObjectId());
                    MarkerOptions markerOpts =
                            new MarkerOptions().position(new LatLng(relato.getLocalizacao().getLatitude(),
                                    relato.getLocalizacao().getLongitude()));
                    markerOpts.title(relato.getDescricao());
                    int icon = iconMakers(relato);
                    markerOpts.icon(BitmapDescriptorFactory.fromResource(icon));
                    final Marker marker = mapa.getMap().addMarker(markerOpts);
                    mapMarkers.put(relato.getObjectId(), marker);
                    if (relato.getObjectId().equals(selectedRelatoObjectId)) {
                        selectedRelatoObjectId = null;
                    }
                }
                cleanUpMarkers(toKeep);
            }
        });
    }

    private int iconMakers(Relato relato) {
        if(relato.getTipoRelato().toString().equals("Luz")) {
            if (relato.getImage() == null) {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.luzcoment;
                } else {
                    return drawable.luz;
                }
            } else {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.luzboth;
                } else {
                    return drawable.luzfoto;
                }
            }
        } else if(relato.getTipoRelato().toString().equals("Agua")) {
            if (relato.getImage() == null) {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.aguacoment;
                } else {
                    return drawable.agua;
                }
            } else {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.aguaboth;
                } else {
                    return drawable.aguafoto;
                }
            }
        } else if(relato.getTipoRelato().toString().equals("Estrada")) {
            if (relato.getImage() == null) {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.estradacoment;
                } else {
                    return drawable.estrada;
                }
            } else {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.estradaboth;
                } else {
                    return drawable.estradafoto;
                }
            }
        } else {
            if (relato.getImage() == null) {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.martelocoment;
                } else {
                    return drawable.martelo;
                }
            } else {
                if (relato.getIDComentarios().size() != 0) {
                    return drawable.marteloboth;
                } else {
                    return drawable.martelofoto;
                }
            }
        }
    }

    private void cleanUpMarkers(Set<String> markersToKeep) {
        for (String objId : new HashSet<String>(mapMarkers.keySet())) {
            if (!markersToKeep.contains(objId)) {
                Marker marker = mapMarkers.get(objId);
                marker.remove();
                mapMarkers.get(objId).remove();
                mapMarkers.remove(objId);
            }
        }
    }

    private String getCityFromLocation(ParseGeoPoint location){
        try{
            List<Address> addresses = this.gcd.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses.size() > 0) {
                return addresses.get(0).getSubAdminArea();
            }
        } catch (Exception e) {
        }
        return "";
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // toggle nav drawer on selecting action bar app icon/title
        switch (item.getItemId()) {
            case R.id.action_settings:
                //openSettings();
                return true;
            case R.id.logout_option:
                logout();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public ParseUser setupUser(String name, String passwd) {
        ParseUser user = new ParseUser();
        user.setUsername(name);
        user.setPassword(passwd);
        return user;
    }

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        ParseGeoPoint parseLocation = geoPointFromLocation(location);
        if (lastLocation != null
                && parseLocation
                .distanceInKilometersTo(geoPointFromLocation(lastLocation)) < 0.01) {
            return;
        }
        lastLocation = location;
        LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (!hasSetUpInitialLocation) {
            // Zoom to the current location.
            updateZoom(myLatLng);
            hasSetUpInitialLocation = true;
        }
    }

    /*
    * Zooms the map to show the area of interest based on the search radius
    */
    private void updateZoom(LatLng myLatLng) {
        // Get the bounds to zoom to
        LatLngBounds bounds = calculateBoundsWithCenter(myLatLng);
        // Zoom to the given bounds
        mapa.getMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 5));
    }

    /*
    * Helper method to calculate the bounds for map zooming
    */
    private LatLngBounds calculateBoundsWithCenter(LatLng myLatLng) {
        // Create a bounds
        LatLngBounds.Builder builder = LatLngBounds.builder();

        // Calculate east/west points that should to be included
        // in the bounds
        double lngDifference = calculateLatLngOffset(myLatLng, false);
        LatLng east = new LatLng(myLatLng.latitude, myLatLng.longitude + lngDifference);
        builder.include(east);
        LatLng west = new LatLng(myLatLng.latitude, myLatLng.longitude - lngDifference);
        builder.include(west);

        // Calculate north/south points that should to be included
        // in the bounds
        double latDifference = calculateLatLngOffset(myLatLng, true);
        LatLng north = new LatLng(myLatLng.latitude + latDifference, myLatLng.longitude);
        builder.include(north);
        LatLng south = new LatLng(myLatLng.latitude - latDifference, myLatLng.longitude);
        builder.include(south);

        return builder.build();
    }

    /*
     * Helper method to calculate the offset for the bounds used in map zooming
    */
    private double calculateLatLngOffset(LatLng myLatLng, boolean bLatOffset) {
        // The return offset, initialized to the default difference
        double latLngOffset = OFFSET_CALCULATION_INIT_DIFF;
        // Set up the desired offset distance in meters
        float desiredOffsetInMeters = radius * METERS_PER_FEET;
        // Variables for the distance calculation
        float[] distance = new float[1];
        boolean foundMax = false;
        double foundMinDiff = 0;
        // Loop through and get the offset
        do {
            // Calculate the distance between the point of interest
            // and the current offset in the latitude or longitude direction
            if (bLatOffset) {
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude
                        + latLngOffset, myLatLng.longitude, distance);
            } else {
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude,
                        myLatLng.longitude + latLngOffset, distance);
            }
            // Compare the current difference with the desired one
            float distanceDiff = distance[0] - desiredOffsetInMeters;
            if (distanceDiff < 0) {
                // Need to catch up to the desired distance
                if (!foundMax) {
                    foundMinDiff = latLngOffset;
                    // Increase the calculated offset
                    latLngOffset *= 2;
                } else {
                    double tmp = latLngOffset;
                    // Increase the calculated offset, at a slower pace
                    latLngOffset += (latLngOffset - foundMinDiff) / 2;
                    foundMinDiff = tmp;
                }
            } else {
                // Overshot the desired distance
                // Decrease the calculated offset
                latLngOffset -= (latLngOffset - foundMinDiff) / 2;
                foundMax = true;
            }
        } while (Math.abs(distance[0] - desiredOffsetInMeters) > OFFSET_CALCULATION_ACCURACY);
        return latLngOffset;
    }

    private void startPeriodicUpdates() {
        locationClient.requestLocationUpdates(locationRequest, this);
    }

    private void stopPeriodicUpdates() {
        locationClient.removeLocationUpdates(this);
    }

    public Location getLocation() {
        if (servicesConnected()) {
            return locationClient.getLastLocation();
        } else {
            return null;
        }
    }

    /*
    * Helper method to get the Parse GEO point representation of a location
    */
    private ParseGeoPoint geoPointFromLocation(Location loc) {
        return new ParseGeoPoint(loc.getLatitude(), loc.getLongitude());
    }

    @Override
    public void onConnected(Bundle bundle) {
        currentLocation = getLocation();
        startPeriodicUpdates();
    }

    @Override
    public void onDisconnected() {
    }

    public void logout() {
        ParseUser.logOut();
        Intent intent = new Intent(InitialActivity.this, DispatchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
            }
        } else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    private boolean servicesConnected() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        if (ConnectionResult.SUCCESS == resultCode) {
            return true;
        } else {
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
            if (dialog != null) {
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(dialog);
                // errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
            }
            return false;
        }
    }

    /*
    * Show a dialog returned by Google Play services for the connection error code
    */
    private void showErrorDialog(int errorCode) {
        // Get the error dialog from Google Play services
        Dialog errorDialog =
                GooglePlayServicesUtil.getErrorDialog(errorCode, this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {

            // Create a new DialogFragment in which to show the error dialog
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();

            // Set the dialog in the DialogFragment
            errorFragment.setDialog(errorDialog);

            // Show the error dialog in the DialogFragment
            //errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
        }
    }

    public LocationClient getLocationClient() {
        return locationClient;
    }

    @Override
    public void onStop() {
        if (locationClient.isConnected()) {
            stopPeriodicUpdates();
        }
        locationClient.disconnect();
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        locationClient.connect();
    }

    @SuppressLint("NewApi")
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;

        /**
         * Default constructor. Sets the dialog field to null
         */
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        /*
         * Set the dialog to display
         *
         * @param dialog An error dialog
         */
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        /*
         * This method must return a Dialog to the DialogFragment.
         */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }
}