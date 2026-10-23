package app.morphe.extension.sabp.ui;

import android.app.Activity;
import android.view.ViewGroup;

import app.morphe.extension.sabp.EppyPlayer;
import app.morphe.extension.sabp.EppyTheme;

/** What the screens need from the activity hosting them. */
public interface Host {
    Activity activity();

    EppyTheme theme();

    EppyPlayer player();

    /** Root view that bottom sheets attach to. */
    ViewGroup overlayRoot();

    void openPlayer();

    void closePlayer();

    void openSabp(String activityClass);

    void showMoreSheet();

    void selectTab(int tab);

    void rebuild();

    void playBook(EppyPlayer.Book book, boolean openPlayer);
}
