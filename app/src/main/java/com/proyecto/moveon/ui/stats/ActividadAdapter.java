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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Adapter del historial de actividades.
 *
 * <p>Muestra una card colapsada con la distancia y tipo de actividad.
 * Al pulsar la cabecera se expande para mostrar calorías, ritmo medio,
 * tiempo en movimiento y tiempo parado.</p>
 *
 * <p>El estado de expansión se gestiona internamente con un {@link Set}
 * de {@code localId} expandidos. No se pierde al hacer scroll.</p>
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
                            && a.duracionParadoSegundos == b.duracionParadoSegundos
                            && a.ritmoMedioMovimientoSegKm == b.ritmoMedioMovimientoSegKm
                            && a.caloriasQuemadas == b.caloriasQuemadas
                            && a.fechaRutaIso.equals(b.fechaRutaIso);
                }
            };

    @NonNull
    private final OnDeleteClickListener deleteListener;

    /** Conjunto de localIds cuya card está actualmente expandida. */
    private final Set<String> expandedIds = new HashSet<>();

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
            final Context context = binding.getRoot().getContext();

            // Icono y tipo de actividad
            String canonicalTipo = ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, item.tipo);
            boolean esCaminar = "Caminar".equals(canonicalTipo);
            binding.ivActivityIcon.setImageResource(
                    esCaminar ? R.drawable.walk_icon : R.drawable.play_icon
            );
            binding.tvActivityType.setText(
                    ProfileValueLocalizer.displayActivityType(context, canonicalTipo)
            );

            // Fecha
            binding.tvActivityDate.setText(formatFecha(item.fechaRutaIso, context));

            // Badge de sincronización pendiente
            boolean pendiente = item.isPendingSync();
            binding.tvPendingBadge.setVisibility(pendiente ? View.VISIBLE : View.GONE);

            // Distancia (siempre visible en cabecera)
            binding.tvActivityDistance.setText(
                    context.getString(R.string.stats_format_km, item.distanciaMetros / 1000.0f)
            );

            // Detalles del panel expandible
            binding.tvActivityCalories.setText(
                    context.getString(R.string.stats_format_kcal, item.caloriasQuemadas)
            );
            binding.tvActivityPace.setText(
                    context.getString(
                            R.string.stats_item_pace_format,
                            formatPace(item.ritmoMedioMovimientoSegKm)
                    )
            );
            binding.tvActivityMoving.setText(
                    formatDuracion(item.duracionMovimientoSegundos, context)
            );
            binding.tvActivityStopped.setText(
                    formatDuracion(item.duracionParadoSegundos, context)
            );

            // Botón borrar (deshabilitado si está pendiente de sync)
            binding.btnDelete.setEnabled(!pendiente);
            binding.btnDelete.setAlpha(pendiente ? 0.3f : 1.0f);
            binding.btnDelete.setOnClickListener(v -> deleteListener.onDeleteClick(item));

            // Estado de expansión — aplicar sin animación durante bind
            applyExpandState(item.localId, false);

            // Toggle expand/collapse al pulsar la cabecera
            binding.layoutHeader.setOnClickListener(v -> toggleExpand(item.localId));
        }

        // ── Expansión ─────────────────────────────────────────────────────────

        /**
         * Alterna el estado expandido para el {@code localId} dado y
         * aplica la transición con animación sobre el chevron.
         */
        private void toggleExpand(@NonNull String localId) {
            if (expandedIds.contains(localId)) {
                expandedIds.remove(localId);
            } else {
                expandedIds.add(localId);
            }
            applyExpandState(localId, true);
        }

        /**
         * Aplica la visibilidad de la sección de detalles y la rotación
         * del chevron según el estado de expansión actual.
         *
         * @param localId   identificador de la actividad
         * @param animate   {@code true} para animar la rotación del chevron
         */
        private void applyExpandState(@NonNull String localId, boolean animate) {
            boolean expanded = expandedIds.contains(localId);
            int detailVisibility = expanded ? View.VISIBLE : View.GONE;

            binding.layoutDetails.setVisibility(detailVisibility);
            binding.viewDivider.setVisibility(detailVisibility);

            float targetRotation = expanded ? 180f : 0f;
            if (animate) {
                binding.ivChevron.animate()
                        .rotation(targetRotation)
                        .setDuration(200)
                        .start();
            } else {
                binding.ivChevron.setRotation(targetRotation);
            }
        }

        // ── Formateo ──────────────────────────────────────────────────────────

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