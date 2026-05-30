package br.fmu.projetoasthmaspace.Presentation.Adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import br.fmu.projetoasthmaspace.Presentation.ActivityView.Poluente;
import br.fmu.projetoasthmaspace.databinding.ItemPoluenteBinding;

public class PoluenteAdapter
        extends RecyclerView.Adapter<PoluenteAdapter.ViewHolder> {

    private List<Poluente> lista;

    public PoluenteAdapter(List<Poluente> lista) {
        this.lista = lista;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemPoluenteBinding binding =
                ItemPoluenteBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {
        Poluente p = lista.get(position);
        Log.d("POLUENTE", "valor raw: [" + p.valor + "]");
        holder.binding.poluenteSigla.setText(p.sigla);
        holder.binding.poluenteNomeCompleto.setText(p.nome);
        holder.binding.poluenteValor.setText(p.valor);
        holder.binding.poluenteStatus.setText(p.status);
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemPoluenteBinding binding;

        ViewHolder(ItemPoluenteBinding b) {
            super(b.getRoot());
            binding = b;
        }
    }
}
