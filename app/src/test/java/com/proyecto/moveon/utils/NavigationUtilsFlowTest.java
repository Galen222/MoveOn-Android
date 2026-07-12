package com.proyecto.moveon.utils;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
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
        try (ActivityController<SourceActivity> controller =
                     Robolectric.buildActivity(SourceActivity.class).setup()) {
            Activity activity = controller.get();

            NavigationUtils.goToActivity(activity, TargetActivity.class);

            ShadowActivity shadow = Shadows.shadowOf(activity);
            Intent started = shadow.getNextStartedActivity();
            assertTargetsTargetActivity(started);
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void goToActivityAndFinish_startsTargetAndFinishesCurrentActivity() {
        try (ActivityController<SourceActivity> controller =
                     Robolectric.buildActivity(SourceActivity.class).setup()) {
            Activity activity = controller.get();

            NavigationUtils.goToActivityAndFinish(activity, TargetActivity.class);

            ShadowActivity shadow = Shadows.shadowOf(activity);
            Intent started = shadow.getNextStartedActivity();
            assertTargetsTargetActivity(started);
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void goToActivityAndClearTask_withApplicationContextAddsClearTaskFlags() {
        Application app = ApplicationProvider.getApplicationContext();

        NavigationUtils.goToActivityAndClearTask(app, TargetActivity.class);

        ShadowApplication shadow = Shadows.shadowOf(app);
        Intent started = shadow.getNextStartedActivity();
        assertTargetsTargetActivity(started);
        assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK,
                started.getFlags() & (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
        );
    }

    @Test
    public void goToActivityAndClearTask_withActivityContextAlsoFinishesActivity() {
        try (ActivityController<SourceActivity> controller =
                     Robolectric.buildActivity(SourceActivity.class).setup()) {
            Activity activity = controller.get();

            NavigationUtils.goToActivityAndClearTask(activity, TargetActivity.class);

            ShadowActivity shadow = Shadows.shadowOf(activity);
            Intent started = shadow.getNextStartedActivity();
            assertTargetsTargetActivity(started);
            assertEquals(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    started.getFlags() & (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
            );
            assertTrue(activity.isFinishing());
        }
    }

    private static void assertTargetsTargetActivity(Intent intent) {
        assertNotNull(intent);
        ComponentName component = intent.getComponent();
        assertNotNull(component);
        assertEquals(TargetActivity.class.getName(), component.getClassName());
    }

    public static class SourceActivity extends Activity {}
    public static class TargetActivity extends Activity {}
}
