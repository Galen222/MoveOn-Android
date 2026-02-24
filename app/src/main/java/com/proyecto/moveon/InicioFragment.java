package com.proyecto.moveon;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class InicioFragment extends Fragment {

    // DECLARACIÓN DE VARIABLES UI
    // Título
    private TextView tvTitle;
    private ImageButton btnAdd; // botón de añadir arriba a la derecha ( nueva ruta)
    // Mapa
    private MaterialCardView cardMap; // los cardView los declaro por si en un futuro se interactua con ellos
    private ImageView ivMapPlaceholder; // la imagen donde ira el GoogleMaps
    private ImageView ivLocationMarker; // el simbolo de ubicación, por si se hace algo con él

    // Estado (Andando/Corriendo)
    private LinearLayout statusWalking; // cambiar entre andando y corriendo
    private LinearLayout statusRunning; // cambiar entre andando y corriendo
    private TextView tvWalking;
    private TextView tvRunning;
    private ImageView ivWalking; // si queremos animar el icono de andar
    private ImageView ivRunning; // si queremos animar el icono de correr

    // Botones de control
    private MaterialCardView cardControls;
    private MaterialButton btnStop;
    private MaterialButton btnPlay;
    private MaterialButton btnReset;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Infla el layout del fragmento (Convertir el archivo XML del fragmento en una pantalla real que Android puede mostrar y usar)
        View view = inflater.inflate(R.layout.fragment_inicio, container, false);
        // Inicializar las referencias a los elementos UI
        initializeViews(view);

        // TODO: Configurar los listeners de los botones


        return view;
    }
    // INICIALIZACIÓN DE VISTAS ( toddo lo "tocable por si queremos añadir funciones a ellos)
    private void initializeViews(View view) {
        // Título
        tvTitle = view.findViewById(R.id.tv_title);
        btnAdd = view.findViewById(R.id.btn_add);

        // Mapa
        cardMap = view.findViewById(R.id.card_map);
        ivMapPlaceholder = view.findViewById(R.id.iv_map_placeholder);
        ivLocationMarker = view.findViewById(R.id.iv_location_marker);

        // Estado
        statusWalking = view.findViewById(R.id.status_walking);
        statusRunning = view.findViewById(R.id.status_running);
        tvWalking = view.findViewById(R.id.tv_walking);
        tvRunning = view.findViewById(R.id.tv_running);
        ivWalking = view.findViewById(R.id.iv_walking);
        ivRunning = view.findViewById(R.id.iv_running);

        // Botones de control
        cardControls = view.findViewById(R.id.card_controls);
        btnStop = view.findViewById(R.id.btn_stop);
        btnPlay = view.findViewById(R.id.btn_play);
        btnReset = view.findViewById(R.id.btn_reset);
    }


    // (TODO)MÉTODOS PARA CAMBIAR ESTADO ---> Con el acelerómetro solamente hay que llamar a los métodos
    /* TODO : ANDANDO
    private void showWalkingStatus() {
    // Activar "Andando" (fondo verde)
    statusWalking.setBackgroundColor(getResources().getColor(R.color.greenPrimary));
    tvWalking.setTextColor(getResources().getColor(R.color.textOnGreen));
    ivWalking.setColorFilter(getResources().getColor(R.color.textOnGreen));

    // Desactivar "Corriendo" (fondo transparente)
    statusRunning.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    tvRunning.setTextColor(getResources().getColor(R.color.textSecondary));
    ivRunning.setColorFilter(getResources().getColor(R.color.textSecondary));
}
     */
    /* TODO: cORRIENDO
    private void showRunningStatus() {
    // Activar "Corriendo" (fondo verde)
    statusRunning.setBackgroundColor(getResources().getColor(R.color.greenPrimary));
    tvRunning.setTextColor(getResources().getColor(R.color.textOnGreen));
    ivRunning.setColorFilter(getResources().getColor(R.color.textOnGreen));

    // Desactivar "Andando" (fondo transparente)
    statusWalking.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    tvWalking.setTextColor(getResources().getColor(R.color.textSecondary));
    ivWalking.setColorFilter(getResources().getColor(R.color.textSecondary));
}
     */

}