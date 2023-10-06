package universe.constellation.orion.viewer.dialog;

import android.app.Dialog;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import universe.constellation.orion.viewer.OrionScene;
import universe.constellation.orion.viewer.OrionViewerActivity;

import static universe.constellation.orion.viewer.LoggerKt.log;

/**
 * User: mike
 * Date: 12.11.13
 * Time: 20:40
 */
public class DialogOverView {

    protected final OrionViewerActivity activity;

    public final Dialog dialog;

    public DialogOverView(OrionViewerActivity activity, int layoutId, int style) {
        this.activity = activity;

        dialog = new Dialog(activity, style);
        dialog.setContentView(layoutId);

    }

    protected void initDialogSize() {
        OrionScene view = activity.getView();
        Rect rect = new Rect(0, 0, view.getSceneWidth(), view.getSceneHeight());
        int width = rect.width();
        int height = rect.height();
        log("Dialog dim: " + width + "x" + height);
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.BOTTOM;
        params.width = width;
        params.height = height;
        window.setAttributes(params);
    }
}
