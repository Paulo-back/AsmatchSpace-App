package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import br.fmu.projetoasthmaspace.Core.Domain.Lembretes.LembreteInstancia;
import br.fmu.projetoasthmaspace.R;

public class TarefasAdapter extends RecyclerView.Adapter<TarefasAdapter.TarefaViewHolder> {

    public interface OnTarefaConcluidaListener {
        void onTarefaConcluida(LembreteInstancia tarefa);
    }

    private final List<LembreteInstancia> lista;
    private final OnTarefaConcluidaListener listener;

    public TarefasAdapter(List<LembreteInstancia> lista, OnTarefaConcluidaListener listener) {
        this.lista    = lista;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TarefaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tarefa_checkbox, parent, false);
        return new TarefaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TarefaViewHolder holder, int position) {
        LembreteInstancia tarefa = lista.get(position);

        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setEnabled(false);
        holder.checkbox.setText(tarefa.titulo + " - " + tarefa.getHorarioFormatado());
        holder.checkbox.setChecked(false);
        holder.checkbox.setEnabled(true);

        holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) listener.onTarefaConcluida(tarefa);
        });
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    public static class TarefaViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkbox;

        public TarefaViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.checkbox_tarefa);
        }
    }
}