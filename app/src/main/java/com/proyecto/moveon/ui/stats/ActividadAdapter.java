package com.proyecto.moveon.ui.stats;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.databinding.ItemActividadBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Adapter para el historial de actividades en {@link StatsFragment}.
 *
 * <p>Usa {@link ListAdapter} con {@link DiffUtil} para actualizaciones eficientes
 * y {@link ItemActividadBinding} (ViewBinding) para acceso seguro a las vistas.
 */
public class ActividadAdapter extends ListAdapter<ActividadItem, ActividadAdapter.ViewHolder> {

    /** Callback para el botón de borrar de cada ítem. */
    public interface OnDeleteClickListener {
        void onDeleteClick(@NonNull ActividadItem item);
    }

    private static final DiffUtil.ItemCallback<ActividadItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull ActividadItem a, @NonNull ActividadItem b) {
                    return a.localId.equals(b.localId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ActividadItem a, @NonNull ActividadItem b) {
                    return a.localId.equals(b.localId)
                            && a.syncState.equals(b.syncState)
                            && a.distanciaMetros == b.distanciaMetros
                            && a.duracionSegundos == b.duracionSegundos
                            && a.caloriasQuemadas == b.caloriasQuemadas
                            && a.fechaRutaIso.equals(b.fechaRutaIso);
                }
            };

    @NonNull
    private final OnDeleteClickListener deleteListener;

    public ActividadAdapter(@NonNull OnDeleteClickListener deleteListener) {
        super(DIFF_CALLBACK);
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemActividadBinding binding = ItemActividadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    public final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemActividadBinding binding;

        ViewHolder(@NonNull ItemActividadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ActividadItem item) {
            Context context = binding.getRoot().getContext();

            // Tipo canónico + label localizado para UI
            String canonicalTipo = ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, item.tipo);
            boolean esCaminar = "Caminar".equals(canonicalTipo);
            binding.ivActivityIcon.setImageResource(
                    esCaminar
                            ? com.proyecto.moveon.R.drawable.walk_icon
                            : com.proyecto.moveon.R.drawable.play_icon);

            binding.tvActivityType.setText(ProfileValueLocalizer.displayActivityType(context, canonicalTipo));

            // Fecha formateada
            binding.tvActivityDate.setText(formatFecha(item.fechaRutaIso, context));

            // Badge pendiente
            boolean pendiente = item.isPendingSync();
            binding.tvPendingBadge.setVisibility(pendiente ? View.VISIBLE : View.GONE);

            // Métricas
            binding.tvActivityDistance.setText(
                    context.getString(
                            com.proyecto.moveon.R.string.stats_format_km,
                            item.distanciaMetros / 1000.0f));

            binding.tvActivityDuration.setText(formatDuracion(item.duracionSegundos, context));

            binding.tvActivityCalories.setText(
                    context.getString(
                            com.proyecto.moveon.R.string.stats_format_kcal,
                            item.caloriasQuemadas));

            // Botón borrar — deshabilitado si está pendiente de sync
            binding.btnDelete.setEnabled(!pendiente);
            binding.btnDelete.setAlpha(pendiente ? 0.3f : 1.0f);
            binding.btnDelete.setOnClickListener(v -> deleteListener.onDeleteClick(item));
        }

        @NonNull
        private String formatFecha(@NonNull String fechaIso, @NonNull Context context) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                        "d MMM yyyy",
                        AppLanguageManager.getActiveLocale(context)
                );
                return OffsetDateTime.parse(fechaIso)
                        .toLocalDate()
                        .format(formatter);
            } catch (DateTimeParseException e) {
                return fechaIso.length() >= 10 ? fechaIso.substring(0, 10) : fechaIso;
            }
        }

        @NonNull
        private String formatDuracion(int segundos, @NonNull Context context) {
            long horas = segundos / 3600L;
            long minutos = (segundos % 3600L) / 60L;
            if (horas > 0) {
                return context.getString(
                        com.proyecto.moveon.R.string.stats_format_time_hm, horas, minutos);
            }
            return context.getString(
                    com.proyecto.moveon.R.string.stats_format_time_m, minutos);
        }
    }
}
