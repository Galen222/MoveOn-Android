package com.proyecto.moveon.utils;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

/**
 * Tests de navegación sobre los Intents reales generados por {@link NavigationUtils}.
 */
@RunWith(RobolectricTestRunner.class)
public class NavigationUtilsFlowTest {

    @Test
    public void goToActivity_startsTargetWithoutFinishingCurrentActivity() {
        Activity activity = Robolectric.buildActivity(SourceActivity.class).setup().get();

        NavigationUtils.goToActivity(activity, TargetActivity.class);

        ShadowActivity shadow = Shadows.shadowOf(activity);
        Intent started = shadow.getNextStartedActivity();
        assertEquals(TargetActivity.class.getName(), started.getComponent().getClassName());
        assertFalse(activity.isFinishing());
    }

    @Test
    public void goToActivityAndFinish_startsTargetAndFinishesCurrentActivity() {
        Activity activity = Robolectric.buildActivity(SourceActivity.class).setup().get();

        NavigationUtils.goToActivityAndFinish(activity, TargetActivity.class);

        ShadowActivity shadow = Shadows.shadowOf(activity);
        Intent started = shadow.getNextStartedActivity();
        assertEquals(TargetActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void goToActivityAndClearTask_withApplicationContextAddsClearTaskFlags() {
        Application app = ApplicationProvider.getApplicationContext();

        NavigationUtils.goToActivityAndClearTask(app, TargetActivity.class);

        ShadowApplication shadow = Shadows.shadowOf(app);
        Intent started = shadow.getNextStartedActivity();
        assertEquals(TargetActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK,
                started.getFlags() & (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
        );
    }

    @Test
    public void goToActivityAndClearTask_withActivityContextAlsoFinishesActivity() {
        Activity activity = Robolectric.buildActivity(SourceActivity.class).setup().get();

        NavigationUtils.goToActivityAndClearTask(activity, TargetActivity.class);

        ShadowActivity shadow = Shadows.shadowOf(activity);
        Intent started = shadow.getNextStartedActivity();
        assertEquals(TargetActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK,
                started.getFlags() & (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
        );
        assertTrue(activity.isFinishing());
    }

    public static class SourceActivity extends Activity {}
    public static class TargetActivity extends Activity {}
}
