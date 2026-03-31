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

public final class RankingAdapter
        extends ListAdapter<RankingItemDto, RankingAdapter.ViewHolder> {

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

    private static int resolvePositionColor(@NonNull Context context, int posicion) {
        if (posicion == 1) return ContextCompat.getColor(context, R.color.ranking_position_gold);
        if (posicion == 2) return ContextCompat.getColor(context, R.color.ranking_position_silver);
        if (posicion == 3) return ContextCompat.getColor(context, R.color.ranking_position_bronze);
        return ContextCompat.getColor(context, R.color.ranking_position_default);
    }

    @DrawableRes
    private static int resolveMedalBackground(int posicion) {
        if (posicion == 1) return R.drawable.bg_ranking_medal_gold;
        if (posicion == 2) return R.drawable.bg_ranking_medal_silver;
        if (posicion == 3) return R.drawable.bg_ranking_medal_bronze;
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

        void bind(@NonNull RankingItemDto item, int posicion) {
            Context context = itemView.getContext();
            tvPosicion.setText(String.format(Locale.US, "%d", posicion));
            tvNombre.setText(item.nombreUsuario);
            tvKm.setText(String.format(Locale.US, "%.2f km", item.totalMetros / 1000.0));
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
