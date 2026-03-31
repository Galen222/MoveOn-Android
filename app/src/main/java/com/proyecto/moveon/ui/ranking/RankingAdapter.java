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
 * <p>Esta versión asume que {@link RankingItemDto} ya incluye el campo {@code posicion}
 * calculado por backend, que es la solución recomendada para el bug de puestos
 * nacionales/provinciales.</p>
 */
public final class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.ViewHolder> {

    /**
     * Callback opcional al pulsar una fila del ranking.
     */
    public interface OnUserClickListener {
        void onUserClick(@NonNull RankingItemDto item);
    }

    @NonNull
    private final List<RankingItemDto> items = new ArrayList<>();

    @Nullable
    private final OnUserClickListener onUserClickListener;

    public RankingAdapter(@Nullable OnUserClickListener onUserClickListener) {
        this.onUserClickListener = onUserClickListener;
        setHasStableIds(true);
    }

    /**
     * Sustituye completamente el contenido actual por una nueva lista.
     *
     * <p>Se hace de forma síncrona con {@link #notifyDataSetChanged()} porque el tamaño del
     * ranking es muy pequeño y aquí prima la ausencia total de flicker sobre la animación
     * incremental.</p>
     */
    public void setItems(@Nullable List<RankingItemDto> newItems) {
        items.clear();
        if (newItems != null && !newItems.isEmpty()) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
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
        items.clear();
        notifyDataSetChanged();
    }

    /**
     * Devuelve una snapshot inmutable por si la UI necesitara inspeccionar el contenido actual.
     */
    @NonNull
    public List<RankingItemDto> getItemsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    @Override
    public long getItemId(int position) {
        RankingItemDto item = items.get(position);
        return item.nombreUsuario != null ? item.nombreUsuario.hashCode() : RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ranking_usuario, parent, false);
        return new ViewHolder(view, onUserClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

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
