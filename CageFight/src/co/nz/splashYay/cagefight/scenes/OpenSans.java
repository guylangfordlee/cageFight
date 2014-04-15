package co.nz.splashYay.cagefight.scenes;

import android.content.Context;
import android.graphics.Typeface;

public class OpenSans {

    private Context context;
    private static OpenSans instance;

    public OpenSans(Context context) {
        this.context = context;
    }

    public static OpenSans getInstance(Context context) {
        synchronized (OpenSans.class) {
            if (instance == null)
                instance = new OpenSans(context);
            return instance;
        }
    }

    public Typeface getTypeFace() {
        return Typeface.createFromAsset(context.getResources().getAssets(),
                "opensans.ttf");
    }
}
