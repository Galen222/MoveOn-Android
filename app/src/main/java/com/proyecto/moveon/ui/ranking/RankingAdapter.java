package com.proyecto.moveon.ui.ranking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.proyecto.moveon.R;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter del ranking SIN AsyncListDiffer / ListAdapter.
 *
 * <p>Motivo de este cambio:</p>
 * <ul>
 *     <li>El ranking muestra como máximo 15 filas.</li>
 *     <li>La pantalla cambia entre ámbitos mutuamente excluyentes
 *     (España / provincia).</li>
 *     <li>En este caso no aporta valor usar {@code ListAdapter}, porque su diff es asíncrono
 *     y puede dejar visible la lista anterior durante un instante mientras calcula y aplica
 *     la transición.</li>
 * </ul>
 *
 * <p>Con un adapter clásico y una lista interna mutable, el vaciado del contenido y la
 * sustitución por la nueva lista son síncronos en el hilo principal. Eso elimina el
 * parpadeo del ranking anterior al cambiar de filtro.</p>
 *
 * <p>Asume que {@link RankingItemDto} ya incluye el campo {@code posicion}
 * calculado por backend, que es la fuente de verdad para los puestos
 * nacionales y provinciales.</p>
 */
public final class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.ViewHolder> {

    /**
     * Callback opcional al pulsar una fila del ranking.
     */
    public interface OnUserClickListener {
        /**
         * Notifica que el usuario ha pulsado una fila concreta del ranking.
         *
         * @param item elemento asociado a la fila pulsada.
         */
        void onUserClick(@NonNull RankingItemDto item);
    }

    @NonNull
    private final List<RankingItemDto> items = new ArrayList<>();

    @Nullable
    private final OnUserClickListener onUserClickListener;

    /**
     * Crea el adapter con un callback opcional para pulsaciones sobre filas.
     *
     * @param onUserClickListener listener invocado al tocar un usuario, o {@code null} si no hay acción.
     */
    public RankingAdapter(@Nullable OnUserClickListener onUserClickListener) {
        this.onUserClickListener = onUserClickListener;
        setHasStableIds(true);
    }

    /**
     * Sustituye completamente el contenido actual por una nueva lista.
     *
     * <p>La sustitución es síncrona y usa eventos de rango precisos. El RecyclerView del
     * ranking no tiene animador, por lo que el cambio sigue siendo inmediato y sin flicker,
     * pero evita invalidar filas ajenas al rango realmente sustituido.</p>
     *
     * @param newItems nuevo snapshot de ranking o {@code null} para vaciar la lista.
     */
    public void setItems(@Nullable List<RankingItemDto> newItems) {
        int previousSize = items.size();
        if (previousSize > 0) {
            items.clear();
            notifyItemRangeRemoved(0, previousSize);
        }

        if (newItems != null && !newItems.isEmpty()) {
            items.addAll(newItems);
            notifyItemRangeInserted(0, items.size());
        }
    }

    /**
     * Vacía el adapter de forma inmediata.
     *
     * <p>Se usa justo antes de lanzar una nueva carga para garantizar que ninguna fila del
     * ranking anterior pueda quedar visible.</p>
     */
    public void clearNow() {
        if (items.isEmpty()) {
            return;
        }
        int previousSize = items.size();
        items.clear();
        notifyItemRangeRemoved(0, previousSize);
    }

    /**
     * Devuelve una snapshot inmutable por si la UI necesitara inspeccionar el contenido actual.
     *
     * @return copia defensiva del ranking actualmente renderizado por el adapter.
     */
    @NonNull
    public List<RankingItemDto> getItemsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * Genera un identificador estable a partir del nombre de usuario para reducir recreaciones innecesarias.
     *
     * @param position posición adaptada dentro de {@link #items}.
     * @return hash del nombre de usuario o {@link RecyclerView#NO_ID} si no existe.
     */
    @Override
    public long getItemId(int position) {
        RankingItemDto item = items.get(position);
        return item.nombreUsuario != null ? item.nombreUsuario.hashCode() : RecyclerView.NO_ID;
    }

    /**
     * Infla la fila visual del ranking y crea su {@link ViewHolder} asociado.
     *
     * @param parent contenedor del RecyclerView.
     * @param viewType tipo de vista solicitado por RecyclerView.
     * @return holder listo para enlazar un {@link RankingItemDto}.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ranking_usuario, parent, false);
        return new ViewHolder(view, onUserClickListener);
    }

    /**
     * Enlaza la fila visible con el elemento de ranking correspondiente.
     *
     * @param holder holder que se va a actualizar.
     * @param position posición del elemento dentro de la lista interna.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /**
     * Indica cuántas filas debe mostrar actualmente el RecyclerView.
     *
     * @return tamaño actual de {@link #items}.
     */
    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Resuelve el color del puesto cuando la posición no usa medalla dedicada.
     *
     * @param context contexto usado para resolver recursos de color.
     * @param posicion puesto visible del usuario dentro del ranking.
     * @return color final que debe aplicar la UI al texto de la posición.
     */
    private static int resolvePositionColor(@NonNull Context context, int posicion) {
        if (posicion == 1) {
            return ContextCompat.getColor(context, R.color.ranking_position_gold);
        }
        if (posicion == 2) {
            return ContextCompat.getColor(context, R.color.ranking_position_silver);
        }
        if (posicion == 3) {
            return ContextCompat.getColor(context, R.color.ranking_position_bronze);
        }
        return ContextCompat.getColor(context, R.color.ranking_position_default);
    }

    /**
     * Selecciona el fondo de medalla para los tres primeros puestos del ranking.
     *
     * @param posicion puesto visible del usuario.
     * @return drawable de medalla o {@code 0} si la posición no tiene tratamiento especial.
     */
    @DrawableRes
    private static int resolveMedalBackground(int posicion) {
        if (posicion == 1) {
            return R.drawable.bg_ranking_medal_gold;
        }
        if (posicion == 2) {
            return R.drawable.bg_ranking_medal_silver;
        }
        if (posicion == 3) {
            return R.drawable.bg_ranking_medal_bronze;
        }
        return 0;
    }

    /**
     * Añade una query param de versión para invalidar la caché de la foto cuando cambia.
     *
     * @param photoUrl URL base de la foto de perfil.
     * @param photoVersion versión devuelta por backend para esa imagen.
     * @return URL final versionada o {@code null} si no hay foto utilizable.
     */
    @Nullable
    private static String buildVersionedPhotoUrl(@Nullable String photoUrl, int photoVersion) {
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            return null;
        }
        return photoUrl + (photoUrl.contains("?") ? "&" : "?") + "v=" + photoVersion;
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvPosicion;
        private final ImageView ivFoto;
        private final TextView tvNombre;
        private final TextView tvKm;
        private final TextView tvPuntos;

        @Nullable
        private final OnUserClickListener onUserClickListener;

        /**
         * Crea un holder ligado a una fila individual del ranking.
         *
         * @param itemView vista raíz inflada para la fila.
         * @param onUserClickListener callback opcional para pulsaciones sobre la fila.
         */
        ViewHolder(@NonNull View itemView,
                   @Nullable OnUserClickListener onUserClickListener) {
            super(itemView);
            this.onUserClickListener = onUserClickListener;
            tvPosicion = itemView.findViewById(R.id.tv_ranking_posicion);
            ivFoto = itemView.findViewById(R.id.iv_ranking_foto);
            tvNombre = itemView.findViewById(R.id.tv_ranking_nombre);
            tvKm = itemView.findViewById(R.id.tv_ranking_km);
            tvPuntos = itemView.findViewById(R.id.tv_ranking_puntos);
        }

        /**
         * Vuelca en la fila todos los datos visibles del usuario, incluida la foto versionada.
         *
         * @param item elemento de ranking que debe representarse en pantalla.
         */
        void bind(@NonNull RankingItemDto item) {
            Context context = itemView.getContext();

            // Solución recomendada: la posición visible viene ya calculada desde backend.
            int posicion = item.posicion;

            tvPosicion.setText(String.valueOf(posicion));
            tvNombre.setText(item.nombreUsuario);
            tvKm.setText(String.format(java.util.Locale.US, "%.2f km", item.totalMetros / 1000.0));
            tvPuntos.setText(context.getString(R.string.ranking_puntos_format, item.totalPuntos));

            int medalBackground = resolveMedalBackground(posicion);
            if (medalBackground != 0) {
                tvPosicion.setBackgroundResource(medalBackground);
                tvPosicion.setTextColor(ContextCompat.getColor(context, android.R.color.black));
            } else {
                tvPosicion.setBackground(null);
                tvPosicion.setTextColor(resolvePositionColor(context, posicion));
            }

            String imageUrl = buildVersionedPhotoUrl(item.fotoPerfil, item.fotoVersion);
            if (imageUrl != null) {
                Glide.with(ivFoto.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(new ObjectKey(imageUrl))
                        .circleCrop()
                        .into(ivFoto);
            } else {
                Glide.with(ivFoto.getContext())
                        .load(R.drawable.default_profile)
                        .circleCrop()
                        .into(ivFoto);
            }

            itemView.setOnClickListener(v -> {
                if (onUserClickListener != null) {
                    onUserClickListener.onUserClick(item);
                }
            });
        }
    }
}
