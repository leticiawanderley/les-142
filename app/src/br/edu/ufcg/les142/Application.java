package br.edu.ufcg.les142;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import br.edu.ufcg.les142.models.Comentario;
import br.edu.ufcg.les142.models.Relato;
import com.parse.Parse;
import com.parse.ParsePush;
import com.parse.SaveCallback;
import com.parse.ParseObject;
import com.parse.*;
import com.parse.PushService;

public class Application extends android.app.Application {
    public static final String APPTAG = "les142";

    // Used to pass location from InitialActivity to PostRelatoActivity
    public static final String INTENT_EXTRA_LOCATION = "location";

    // Debugging switch
    public static final boolean APPDEBUG = false;

    private static SharedPreferences preferences;

    private static ConfigHelper configHelper;


    @Override
    public void onCreate() {
        super.onCreate();
        ParseObject.registerSubclass(Relato.class);
        ParseObject.registerSubclass(Comentario.class);
        Parse.enableLocalDatastore(this);
        Parse.initialize(this, "6RkiEquhut1FmiAGjZ7bINdRLI02r5GAFFxVdXdK", "RAQIhDUzSDYAIzNBMw6i5gLPyf3cuT3zEXSuS5a1");
        preferences = getSharedPreferences("com.parse.les142", Context.MODE_PRIVATE);
        configHelper = new ConfigHelper();
        configHelper.fetchConfigIfNeeded();
        //Security of data
        ParseACL defaultACL = new ParseACL();
        // If you would like objects to be private by default, remove this line.
        defaultACL.setPublicReadAccess(true);
        ParseACL.setDefaultACL(defaultACL, true);


        ParseInstallation pi = ParseInstallation.getCurrentInstallation();



    }

    public static ConfigHelper getConfigHelper() {
        return configHelper;
    }
}
