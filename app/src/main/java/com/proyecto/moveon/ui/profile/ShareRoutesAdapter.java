package com.proyecto.moveon.ui.profile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.proyecto.moveon.R;
import com.proyecto.moveon.domain.activity.ActividadItem;

/**
 * Adapter del listado de rutas mostradas en el bottom sheet de compartir.
 *
 * <p>Cada fila resume tipo, fecha, distancia, duración, ritmo y calorías.
 * El ritmo de la línea resumen se pinta ya con la unidad {@code /km} para
 * que el usuario vea el mismo formato que en el resto de pantallas.</p>
 */
public class ShareRoutesAdapter extends ListAdapter<ActividadItem, ShareRoutesAdapter.RouteViewHolder> {

    /**
     * Callback simple para propagar el clic de una ruta al fragmento.
     */
    /**
     * Callback simple para propagar al fragmento la ruta pulsada.
     */
    public interface OnRouteClickListener {
        void onRouteClick(@NonNull ActividadItem item);
    }

    private static final DiffUtil.ItemCallback<ActividadItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ActividadItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull ActividadItem oldItem,
                                               @NonNull ActividadItem newItem) {
                    return oldItem.localId.equals(newItem.localId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ActividadItem oldItem,
                                                  @NonNull ActividadItem newItem) {
                    if (!oldItem.localId.equals(newItem.localId)) return false;
                    if (!oldItem.fechaRutaIso.equals(newItem.fechaRutaIso)) return false;
                    if (oldItem.distanciaMetros != newItem.distanciaMetros) return false;
                    if (oldItem.duracionSegundos != newItem.duracionSegundos) return false;
                    if (oldItem.caloriasQuemadas != newItem.caloriasQuemadas) return false;

                    if (oldItem.rutaPolilinea == null && newItem.rutaPolilinea == null) return true;
                    if (oldItem.rutaPolilinea == null || newItem.rutaPolilinea == null) return false;
                    return oldItem.rutaPolilinea.equals(newItem.rutaPolilinea);
                }
            };

    @NonNull
    private final OnRouteClickListener listener;

    /**
     * @param listener callback invocado al pulsar una fila del listado.
     */
    public ShareRoutesAdapter(@NonNull OnRouteClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_share_route, parent, false);
        return new RouteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    final class RouteViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDate;
        private final TextView tvSummary;
        private final TextView tvSecondary;

        RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvShareRouteTitle);
            tvDate = itemView.findViewById(R.id.tvShareRouteDate);
            tvSummary = itemView.findViewById(R.id.tvShareRouteSummary);
            tvSecondary = itemView.findViewById(R.id.tvShareRouteSecondary);
        }

        /**
         * Vincula los datos de una actividad con la UI de la fila.
         */
        void bind(@NonNull ActividadItem item) {
            Context context = itemView.getContext();

            String activityType = ShareRouteFormatter.displayType(context, item);
            String date = ShareRouteFormatter.formatDate(context, item.fechaRutaIso);
            String distance = ShareRouteFormatter.formatDistance(context, item.distanciaMetros);
            String duration = ShareRouteFormatter.formatDuration(context, item.duracionSegundos);
            String calories = context.getString(R.string.share_routes_kcal_value, item.caloriasQuemadas);
            // El resumen del listado debe mostrar explícitamente la unidad final "/km"
            // para mantener consistencia con la tarjeta compartida y con el historial.
            String pace = ShareRouteFormatter.formatPaceWithUnit(
                    context,
                    item.ritmoMedioTotalSegKm
            );

            tvTitle.setText(activityType);
            tvDate.setText(date);
            tvSummary.setText(context.getString(
                    R.string.share_routes_item_summary,
                    distance,
                    duration,
                    pace
            ));
            tvSecondary.setText(context.getString(
                    R.string.share_routes_item_secondary,
                    calories,
                    item.rutaPolilinea != null
                            ? context.getString(R.string.share_routes_polyline_available)
                            : context.getString(R.string.share_routes_polyline_missing)
            ));

            itemView.setOnClickListener(v -> listener.onRouteClick(item));
        }
    }
}
