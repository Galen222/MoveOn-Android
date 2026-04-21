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
import com.proyecto.moveon.core.settings.PaceDisplayUtils;
import com.proyecto.moveon.databinding.ItemActividadBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Adapter del historial de actividades mostrado en estadísticas.
 *
 * <p>La cabecera de cada tarjeta muestra siempre la distancia y la duración total. Al pulsar
 * la cabecera se expande un bloque de detalle con métricas adicionales, acciones de borrado y
 * compartir, y el badge de sincronización pendiente.</p>
 *
 * <p>Además, el adapter fuerza aquí el formato visual de ritmos para que el detalle expandido
 * siempre muestre un valor inequívoco del tipo {@code mm'ss"/km}, sin depender de composiciones
 * posteriores en la vista.</p>
 */
public class ActividadAdapter extends ListAdapter<ActividadItem, ActividadAdapter.ViewHolder> {

    /** Callback para propagar la acción de borrado al fragmento/pantalla contenedora. */
    public interface OnDeleteClickListener {
        /**
         * Propaga al contenedor la petición de borrar una actividad concreta.
         *
         * @param item actividad sobre la que el usuario pulsó borrar.
         */
        void onDeleteClick(@NonNull ActividadItem item);
    }

    /** Callback para propagar la acción de compartir/ver ruta al contenedor. */
    public interface OnShareClickListener {
        /**
         * Propaga al contenedor la petición de compartir o previsualizar la ruta de una actividad.
         *
         * @param item actividad cuya ruta se quiere compartir.
         */
        void onShareClick(@NonNull ActividadItem item);
    }

    /**
     * DiffUtil del historial.
     *
     * <p>Además de las métricas básicas, se compara también el ritmo máximo para que la fila
     * se refresque correctamente si el backend o una resincronización cambian ese valor.</p>
     */
    private static final DiffUtil.ItemCallback<ActividadItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                /**
                 * Decide si dos filas representan la misma actividad usando el {@code localId} estable.
                 *
                 * @param a elemento antiguo del adapter.
                 * @param b elemento nuevo del adapter.
                 * @return {@code true} si ambos apuntan a la misma actividad lógica.
                 */
                @Override
                public boolean areItemsTheSame(@NonNull ActividadItem a, @NonNull ActividadItem b) {
                    return a.localId.equals(b.localId);
                }

                /**
                 * Comprueba si han cambiado los campos que alteran el render de la tarjeta o sus acciones.
                 *
                 * @param a elemento antiguo del adapter.
                 * @param b elemento nuevo del adapter.
                 * @return {@code true} si la fila puede reutilizarse sin rebinding visible.
                 */
                @Override
                public boolean areContentsTheSame(@NonNull ActividadItem a, @NonNull ActividadItem b) {
                    return a.localId.equals(b.localId)
                            && a.syncState.equals(b.syncState)
                            && a.distanciaMetros == b.distanciaMetros
                            && a.duracionSegundos == b.duracionSegundos
                            && a.duracionMovimientoSegundos == b.duracionMovimientoSegundos
                            && a.duracionParadoSegundos == b.duracionParadoSegundos
                            && a.ritmoMedioMovimientoSegKm == b.ritmoMedioMovimientoSegKm
                            && a.ritmoMedioTotalSegKm == b.ritmoMedioTotalSegKm
                            && a.ritmoMaximoSegKm == b.ritmoMaximoSegKm
                            && a.caloriasQuemadas == b.caloriasQuemadas
                            && a.fechaRutaIso.equals(b.fechaRutaIso)
                            && safeEquals(a.rutaPolilinea, b.rutaPolilinea);
                }
            };

    @NonNull
    private final OnDeleteClickListener deleteListener;

    @NonNull
    private final OnShareClickListener shareListener;

    /** Conjunto de {@code localId} cuya tarjeta está actualmente expandida. */
    private final Set<String> expandedIds = new HashSet<>();

    /**
     * Crea el adapter del historial con los callbacks que ejecutará cada acción de la fila.
     *
     * @param deleteListener receptor de la acción de borrado.
     * @param shareListener receptor de la acción de compartir ruta.
     */
    public ActividadAdapter(@NonNull OnDeleteClickListener deleteListener,
                            @NonNull OnShareClickListener shareListener) {
        super(DIFF_CALLBACK);
        this.deleteListener = deleteListener;
        this.shareListener = shareListener;
    }

    /**
     * Infla la tarjeta visual de una actividad del historial.
     *
     * @param parent RecyclerView que contendrá la fila.
     * @param viewType tipo de vista solicitado por el adapter.
     * @return {@link ViewHolder} listo para enlazar una actividad.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemActividadBinding binding = ItemActividadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    /**
     * Enlaza la actividad situada en la posición indicada con la vista reciclada correspondiente.
     *
     * @param holder holder que recibirá los datos.
     * @param position posición del elemento dentro de la lista actual.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /** ViewHolder de cada tarjeta del historial. */
    public final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemActividadBinding binding;

        /**
         * Crea el holder de una tarjeta del historial y conserva su binding para rebinding posterior.
         *
         * @param binding binding ya inflado de {@link ItemActividadBinding}.
         */
        ViewHolder(@NonNull ItemActividadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Vincula una actividad con la tarjeta del historial.
         *
         * <p>Los campos de ritmo del detalle expandido se rellenan con un formateador
         * propio que añade siempre {@code /km} al final cuando hay un ritmo
         * válido, y también lo mantiene en el placeholder cuando el ritmo no existe.</p>
         *
         * @param item actividad cuyo contenido debe reflejarse en la fila actual.
         */
        void bind(@NonNull ActividadItem item) {
            final Context context = binding.getRoot().getContext();

            // Resolvemos el tipo canónico para mostrar siempre el icono y el label correctos.
            String canonicalTipo = ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, item.tipo);
            final int iconRes;
            if ("Caminar".equals(canonicalTipo)) {
                iconRes = R.drawable.walk_icon;
            } else if ("Correr".equals(canonicalTipo)) {
                iconRes = R.drawable.run_icon;
            } else {
                iconRes = R.drawable.walk_icon;
            }
            binding.ivActivityIcon.setImageResource(iconRes);
            binding.tvActivityType.setText(
                    ProfileValueLocalizer.displayActivityType(context, canonicalTipo)
            );

            // La fecha se normaliza a la zona local igual que en las estadísticas.
            binding.tvActivityDate.setText(formatFecha(item.fechaRutaIso, context));

            // Badge visual para actividades aún no sincronizadas.
            boolean pendiente = item.isPendingSync();
            binding.tvPendingBadge.setVisibility(pendiente ? View.VISIBLE : View.GONE);

            // Cabecera: visible aunque la tarjeta esté colapsada.
            binding.tvActivityDistance.setText(
                    context.getString(R.string.stats_format_km, item.distanciaMetros / 1000.0f)
            );
            binding.tvActivityDuration.setText(
                    formatDuracion(item.duracionSegundos, context)
            );

            // Detalle expandido.
            binding.tvActivityCalories.setText(
                    context.getString(R.string.stats_format_kcal, item.caloriasQuemadas)
            );

            // La unidad "/km" se compone siempre en el valor visible del ritmo.
            binding.tvActivityPace.setText(
                    formatPaceWithUnit(PaceDisplayUtils.getPreferredAveragePaceSeconds(context, item))
            );
            binding.tvActivityMaxPace.setText(
                    formatPaceWithUnit(item.ritmoMaximoSegKm)
            );

            binding.tvActivityMoving.setText(
                    formatDuracion(item.duracionMovimientoSegundos, context)
            );
            binding.tvActivityStopped.setText(
                    formatDuracion(item.duracionParadoSegundos, context)
            );
            binding.tvActivityTotal.setText(
                    formatDuracion(item.duracionSegundos, context)
            );

            // Si la actividad está pendiente de sync no permitimos borrarla para evitar estados raros.
            binding.btnDelete.setEnabled(!pendiente);
            binding.btnDelete.setAlpha(pendiente ? 0.3f : 1.0f);
            binding.btnDelete.setOnClickListener(v -> deleteListener.onDeleteClick(item));

            // El botón de ruta/compartir solo tiene sentido si existe polilínea guardada.
            boolean tienePolilinea = item.rutaPolilinea != null && !item.rutaPolilinea.isEmpty();
            binding.btnShareRoute.setVisibility(tienePolilinea ? View.VISIBLE : View.GONE);
            binding.viewShareDivider.setVisibility(tienePolilinea ? View.VISIBLE : View.GONE);
            if (tienePolilinea) {
                binding.btnShareRoute.setOnClickListener(v -> shareListener.onShareClick(item));
            } else {
                binding.btnShareRoute.setOnClickListener(null);
            }

            // Aplicamos el estado de expansión sin animación durante el bind para reciclar bien la vista.
            applyExpandState(item.localId, false);

            // La cabecera hace toggle del detalle expandido.
            binding.layoutHeader.setOnClickListener(v -> toggleExpand(item.localId));
        }

        /**
         * Alterna el estado expandido/colapsado de una tarjeta.
         *
         * @param localId identificador local de la actividad pulsada.
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
         * Aplica visualmente el estado expandido.
         *
         * @param localId   id local estable de la actividad.
         * @param animate   {@code true} para animar el chevron; {@code false} durante el bind.
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

        /**
         * Formatea la fecha de la actividad usando la zona horaria local del dispositivo.
         *
         * <p>Esto mantiene alineada la fecha visual con la usada por los cálculos de estadísticas,
         * evitando que una misma actividad aparezca en distinto día según la pantalla.</p>
         *
         * @param fechaIso fecha de la actividad en formato ISO.
         * @param context contexto usado para obtener los patrones localizados.
         * @return fecha visible adaptada a la zona local del usuario.
         */
        @NonNull
        private String formatFecha(@NonNull String fechaIso, @NonNull Context context) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                        "d MMM yyyy",
                        AppLanguageManager.getActiveLocale(context)
                );
                return OffsetDateTime.parse(fechaIso)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(formatter);
            } catch (DateTimeParseException e) {
                return fechaIso.length() >= 10 ? fechaIso.substring(0, 10) : fechaIso;
            }
        }

        /**
         * Formatea duraciones como minutos u horas y minutos, según corresponda.
         *
         * @param segundos duración total en segundos.
         * @param context contexto usado para acceder a recursos pluralizables.
         * @return texto de duración listo para la fila del histórico.
         */
        @NonNull
        private String formatDuracion(int segundos, @NonNull Context context) {
            long horas = segundos / 3600L;
            long minutos = (segundos % 3600L) / 60L;
            if (horas > 0L) {
                return context.getString(R.string.stats_format_time_hm, horas, minutos);
            }
            return context.getString(R.string.stats_format_time_m, Math.max(1L, minutos));
        }

        /**
         * Devuelve el ritmo en formato base {@code mm'ss"} sin unidad.
         *
         * <p>Se separa de {@link #formatPaceWithUnit(int)} para dejar claro qué parte del valor
         * es el tiempo puro y qué parte es la unidad visual que exige esta pantalla.</p>
         *
         * @param secondsPerKm ritmo expresado en segundos por kilómetro.
         * @return valor formateado del ritmo sin sufijo de unidad.
         */
        @NonNull
        private String formatPaceValue(int secondsPerKm) {
            if (secondsPerKm <= 0) {
                return "--'--\"";
            }

            int minutes = secondsPerKm / 60;
            int seconds = secondsPerKm % 60;
            return String.format(Locale.US, "%d'%02d\"", minutes, seconds);
        }

        /**
         * Devuelve el ritmo con unidad fija para el detalle del historial.
         *
         * <p>Se devuelve siempre con el sufijo {@code /km}, también para el placeholder,
         * para que visualmente el bloque de métricas sea homogéneo y no dependa de estilos,
         * resources o transformaciones externas.</p>
         *
         * @param secondsPerKm ritmo expresado en segundos por kilómetro.
         * @return ritmo final listo para mostrarse en el detalle expandido.
         */
        @NonNull
        private String formatPaceWithUnit(int secondsPerKm) {
            return formatPaceValue(secondsPerKm) + "/km";
        }
    }

    /**
     * Comparación null-safe para campos opcionales usados por el DiffUtil.
     *
     * @param a primer valor opcional.
     * @param b segundo valor opcional.
     * @return {@code true} cuando ambos valores son equivalentes o ambos nulos.
     */
    private static boolean safeEquals(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }
}