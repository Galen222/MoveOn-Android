package com.proyecto.moveon.ui.stats;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.databinding.ItemActividadBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Adapter del historial de actividades.
 *
 * <p>Se muestran ahora tiempo de movimiento frente a total y el ritmo medio
 * en movimiento, que son los campos más útiles del nuevo modelo.</p>
 */
public class ActividadAdapter extends ListAdapter<ActividadItem, ActividadAdapter.ViewHolder> {

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
                            && a.duracionMovimientoSegundos == b.duracionMovimientoSegundos
                            && a.ritmoMedioMovimientoSegKm == b.ritmoMedioMovimientoSegKm
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

    public final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemActividadBinding binding;

        ViewHolder(@NonNull ItemActividadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ActividadItem item) {
            Context context = binding.getRoot().getContext();

            String canonicalTipo = ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, item.tipo);
            boolean esCaminar = "Caminar".equals(canonicalTipo);
            binding.ivActivityIcon.setImageResource(
                    esCaminar ? R.drawable.walk_icon : R.drawable.play_icon
            );
            binding.tvActivityType.setText(ProfileValueLocalizer.displayActivityType(context, canonicalTipo));
            binding.tvActivityDate.setText(formatFecha(item.fechaRutaIso, context));

            boolean pendiente = item.isPendingSync();
            binding.tvPendingBadge.setVisibility(pendiente ? View.VISIBLE : View.GONE);

            binding.tvActivityDistance.setText(
                    context.getString(R.string.stats_format_km, item.distanciaMetros / 1000.0f)
            );

            binding.tvActivityDuration.setText(
                    context.getString(
                            R.string.stats_activity_duration_breakdown,
                            formatDuracion(item.duracionMovimientoSegundos, context),
                            formatDuracion(item.duracionSegundos, context)
                    )
            );

            binding.tvActivityCalories.setText(
                    context.getString(
                            R.string.stats_activity_kcal_and_pace,
                            item.caloriasQuemadas,
                            formatPace(item.ritmoMedioMovimientoSegKm)
                    )
            );

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
                return context.getString(R.string.stats_format_time_hm, horas, minutos);
            }
            return context.getString(R.string.stats_format_time_m, Math.max(1L, minutos));
        }

        @NonNull
        private String formatPace(int secondsPerKm) {
            if (secondsPerKm <= 0) {
                return "--'--\"";
            }
            int minutes = secondsPerKm / 60;
            int seconds = secondsPerKm % 60;
            return String.format(Locale.US, "%d'%02d\"", minutes, seconds);
        }
    }
}
