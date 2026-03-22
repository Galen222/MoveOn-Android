package com.proyecto.moveon.ui.ranking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.proyecto.moveon.R;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;

import java.util.Locale;
import java.util.Objects;

/**
 * Adapter del ranking.
 *
 * <p>Además de pintar cada fila, expone un callback para detectar cuándo el
 * usuario pulsa sobre un integrante del ranking. Ese clic abre un bottom sheet
 * inferior con acciones rápidas, incluyendo el nuevo flujo de reporte.</p>
 */
public final class RankingAdapter
        extends ListAdapter<RankingItemDto, RankingAdapter.ViewHolder> {

    /**
     * Listener para informar al Fragment del usuario pulsado.
     */
    public interface OnUserClickListener {
        void onUserClick(@NonNull RankingItemDto item);
    }

    private static final DiffUtil.ItemCallback<RankingItemDto> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull RankingItemDto a, @NonNull RankingItemDto b) {
                    return a.nombreUsuario.equals(b.nombreUsuario);
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull RankingItemDto a, @NonNull RankingItemDto b) {
                    return a.totalPuntos == b.totalPuntos
                            && a.totalMetros == b.totalMetros
                            && a.fotoVersion == b.fotoVersion
                            && Objects.equals(a.fotoPerfil, b.fotoPerfil);
                }
            };

    @Nullable
    private final OnUserClickListener onUserClickListener;

    /**
     * @param onUserClickListener callback opcional para abrir el panel de acciones del usuario.
     */
    public RankingAdapter(@Nullable OnUserClickListener onUserClickListener) {
        super(DIFF);
        this.onUserClickListener = onUserClickListener;
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
        holder.bind(getItem(position), position + 1);
    }

    /**
     * Resuelve el color de la posición usando recursos para respetar tema claro/oscuro.
     */
    private static int resolvePositionColor(@NonNull Context context, int posicion) {
        if (posicion == 1) return ContextCompat.getColor(context, R.color.ranking_position_gold);
        if (posicion == 2) return ContextCompat.getColor(context, R.color.ranking_position_silver);
        if (posicion == 3) return ContextCompat.getColor(context, R.color.ranking_position_bronze);
        return ContextCompat.getColor(context, R.color.ranking_position_default);
    }

    /**
     * Construye una URL de imagen cache-busting cuando existe foto remota.
     *
     * <p>La app ya usa {@code fotoVersion} como versión lógica de la foto. Si
     * llega una URL no vacía, se le añade el parámetro {@code v=} para evitar
     * mostrar una imagen obsoleta en caché.</p>
     */
    @Nullable
    private static String buildVersionedPhotoUrl(@Nullable String photoUrl, int photoVersion) {
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            return null;
        }
        return photoUrl + (photoUrl.contains("?") ? "&" : "?") + "v=" + photoVersion;
    }

    /**
     * ViewHolder simple de la fila del ranking.
     */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvPosicion;
        private final ImageView ivFoto;
        private final TextView tvNombre;
        private final TextView tvKm;
        private final TextView tvPuntos;
        @Nullable private final OnUserClickListener onUserClickListener;

        ViewHolder(@NonNull View itemView,
                   @Nullable OnUserClickListener onUserClickListener) {
            super(itemView);
            this.onUserClickListener = onUserClickListener;
            tvPosicion = itemView.findViewById(R.id.tv_ranking_posicion);
            ivFoto     = itemView.findViewById(R.id.iv_ranking_foto);
            tvNombre   = itemView.findViewById(R.id.tv_ranking_nombre);
            tvKm       = itemView.findViewById(R.id.tv_ranking_km);
            tvPuntos   = itemView.findViewById(R.id.tv_ranking_puntos);
        }

        /**
         * Vincula los datos del usuario a la fila visible.
         */
        void bind(@NonNull RankingItemDto item, int posicion) {
            tvPosicion.setText(String.format(Locale.US, "%d", posicion));
            tvNombre.setText(item.nombreUsuario);
            tvKm.setText(String.format(Locale.US, "%.2f km", item.totalMetros / 1000.0));
            tvPuntos.setText(itemView.getContext()
                    .getString(R.string.ranking_puntos_format, item.totalPuntos));
            tvPosicion.setTextColor(resolvePositionColor(itemView.getContext(), posicion));

            // Render de la foto con placeholder consistente y versión de caché.
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

            // La pulsación se delega al Fragment para abrir el bottom sheet inferior.
            itemView.setOnClickListener(v -> {
                if (onUserClickListener != null) {
                    onUserClickListener.onUserClick(item);
                }
            });
        }
    }
}
