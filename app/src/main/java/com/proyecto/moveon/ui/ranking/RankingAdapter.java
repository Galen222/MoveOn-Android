package com.proyecto.moveon.ui.ranking;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        extends ListAdapter<RankingItemDto, RecyclerView.ViewHolder> {

    private static final int COLOR_ORO      = Color.parseColor("#F5A623");
    private static final int COLOR_PLATA    = Color.parseColor("#9B9B9B");
    private static final int COLOR_BRONCE   = Color.parseColor("#C47A2E");
    private static final int COLOR_NORMAL   = Color.parseColor("#374151");

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

    public RankingAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ranking_usuario, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((ViewHolder) holder).bind(getItem(position), position + 1);
    }

    private static final class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvPosicion;
        private final ImageView ivFoto;
        private final TextView tvNombre;
        private final TextView tvKm;
        private final TextView tvPuntos;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPosicion = itemView.findViewById(R.id.tv_ranking_posicion);
            ivFoto     = itemView.findViewById(R.id.iv_ranking_foto);
            tvNombre   = itemView.findViewById(R.id.tv_ranking_nombre);
            tvKm       = itemView.findViewById(R.id.tv_ranking_km);
            tvPuntos   = itemView.findViewById(R.id.tv_ranking_puntos);
        }

        void bind(@NonNull RankingItemDto item, int posicion) {
            tvPosicion.setText(String.format(Locale.US, "%d", posicion));
            tvNombre.setText(item.nombreUsuario);
            tvKm.setText(String.format(Locale.US, "%.2f km", item.totalMetros / 1000.0));
            tvPuntos.setText(itemView.getContext()
                    .getString(R.string.ranking_puntos_format, item.totalPuntos));

            int posColor;
            switch (posicion) {
                case 1:  posColor = COLOR_ORO;    break;
                case 2:  posColor = COLOR_PLATA;  break;
                case 3:  posColor = COLOR_BRONCE; break;
                default: posColor = COLOR_NORMAL; break;
            }
            tvPosicion.setTextColor(posColor);

            if (item.fotoPerfil != null && !item.fotoPerfil.isEmpty()) {
                String url = item.fotoPerfil
                        + (item.fotoPerfil.contains("?") ? "&" : "?")
                        + "v=" + item.fotoVersion;
                Glide.with(ivFoto.getContext())
                        .load(url)
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(new ObjectKey(url))
                        .circleCrop()
                        .into(ivFoto);
            } else {
                Glide.with(ivFoto.getContext())
                        .load(R.drawable.default_profile)
                        .circleCrop()
                        .into(ivFoto);
            }
        }
    }
}