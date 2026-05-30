package br.fmu.projetoasthmaspace.Presentation.Fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.databinding.FragmentEducativoBinding;

public class EducativoFragment extends Fragment {

    private FragmentEducativoBinding binding;
    private List<Artigo> artigos = new ArrayList<>();

    private static class Artigo {
        String titulo;
        String resumo;
        String conteudo;
        int imagemResId;

        Artigo(String titulo, String resumo, String conteudo, @DrawableRes int imagemResId) {
            this.titulo = titulo;
            this.resumo = resumo;
            this.conteudo = conteudo;
            this.imagemResId = imagemResId;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEducativoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (artigos.isEmpty()) {
            preencherListaDeArtigos();
        }

        binding.containerArtigos.removeAllViews();
        for (Artigo artigo : artigos) {
            adicionarArtigo(artigo);
        }
    }

    private void preencherListaDeArtigos() {
        // Artigo sobre AQI (agora no topo)
        artigos.add(new Artigo(
            "O que é o Índice de Qualidade do Ar (AQI)?",
            "Entenda como o AQI traduz os níveis de poluição em um indicador de saúde.",
            "O Índice de Qualidade do Ar (AQI) é uma escala numérica usada para comunicar ao público quão poluído o ar está atualmente ou quão poluído se prevê que se torne. À medida que o AQI aumenta, um percentual maior da população pode sentir efeitos adversos à saúde. Os valores vão de 0 (Bom) a mais de 300 (Perigoso), e cada nível corresponde a uma recomendação de saúde específica, ajudando você a tomar decisões para proteger seu sistema respiratório.",
            R.drawable.aqi_card // TODO: Usar um ícone mais apropriado
        ));
        
        // Artigos sobre Poluentes
        artigos.add(new Artigo(
            "O que são Partículas Finas (PM2.5)?",
            "Entenda um dos poluentes mais perigosos para a saúde respiratória.",
            "As PM2.5 são partículas minúsculas em suspensão no ar, com diâmetro de 2.5 micrômetros ou menos. Por serem tão pequenas, elas podem penetrar profundamente nos pulmões e até mesmo na corrente sanguínea, causando inflamação, agravando a asma e aumentando o risco de problemas cardiovasculares.",
            R.drawable.pm25_card // TODO: Usar um ícone mais apropriado
        ));
        artigos.add(new Artigo(
            "O que são Partículas Inaláveis (PM10)?",
            "Saiba mais sobre as partículas maiores, mas ainda perigosas.",
            "As PM10 são partículas com diâmetro de 10 micrômetros ou menos. Elas são maiores que as PM2.5, mas ainda podem ser inaladas e se alojar nos pulmões, causando irritação nas vias aéreas, tosse, e dificuldade para respirar, especialmente em pessoas com asma.",
            R.drawable.pm10_card// TODO: Usar um ícone mais apropriado
        ));
        artigos.add(new Artigo(
            "O que é o Ozônio (O₃)?",
            "Conheça o 'poluente do bom tempo' e seus riscos.",
            "O ozônio ao nível do solo é um gás irritante formado pela reação da luz solar com outros poluentes. Ele é um dos principais componentes da 'névoa fotoquímica' e pode causar dor no peito, tosse, irritação na garganta e agravar doenças respiratórias como a asma.",
            R.drawable.ozonio_card // TODO: Usar um ícone mais apropriado
        ));
        artigos.add(new Artigo(
            "O que é o Dióxido de Nitrogênio (NO₂)?",
            "Entenda este gás comum em áreas urbanas.",
            "O NO₂ é um gás avermelhado-marrom emitido principalmente pela queima de combustíveis em veículos e usinas de energia. A exposição a curto prazo pode agravar doenças respiratórias, enquanto a exposição a longo prazo pode contribuir para o desenvolvimento de asma.",
            R.drawable.nitrogenio_card // TODO: Usar um ícone mais apropriado
        ));
        artigos.add(new Artigo(
            "O que é o Dióxido de Enxofre (SO₂)?",
            "Saiba mais sobre este poluente industrial.",
            "O SO₂ é um gás incolor com um cheiro forte, emitido principalmente pela queima de combustíveis fósseis em usinas de energia e processos industriais. Ele pode irritar o sistema respiratório e agravar a asma, especialmente durante atividades físicas.",
            R.drawable.dioxido_de_enxofre_card // TODO: Usar um ícone mais apropriado
        ));
        artigos.add(new Artigo(
            "O que é o Monóxido de Carbono (CO)?",
            "Conheça os perigos do 'assassino silencioso'.",
            "O CO é um gás inodoro e incolor produzido pela queima incompleta de combustíveis. Em altas concentrações, ele reduz a quantidade de oxigênio que pode ser transportada no sangue, o que é especialmente perigoso para pessoas com doenças cardíacas e respiratórias.",
            R.drawable.carbono_card // TODO: Usar um ícone mais apropriado
        ));

        // Artigos Originais
        artigos.add(new Artigo("Aonde Guardar seu Inalador", "Um guia sobre os melhores locais para armazenar seu inalador.",
                "Um guia sobre os melhores locais para armazenar seu inalador.\n\n" +
                        "Guardar o inalador corretamente garante que ele funcione quando você precisar.\n\n" +
                        "✅ Recomendado: temperatura ambiente (15°C–25°C), dentro de estojo na bolsa ou na cabeceira da cama.\n\n" +
                        "❌ Evite: porta-luvas do carro (calor), banheiro (umidade) e geladeira (frio reduz eficácia).\n\n" +
                        "\uD83D\uDCA1 Agite antes de usar e verifique sempre a validade.", R.drawable.inalador_guardar_card));
        artigos.add(new Artigo("Como Limpar seu Espaçador",
                "Um guia passo a passo para manter seu Inalador limpo.",
                        "Um guia passo a passo para manter seu inalador limpo.\n\n" +
                        "Limpe o inalador semanalmente para evitar acúmulo de resíduos e garantir a dose correta.\n\n" +
                        "\uD83E\uDDFC Como limpar: desmonte, mergulhe em água morna com detergente neutro por 15 min, enxágue e seque ao ar livre. Não esfregue — cria eletrostática.\n\n" +
                        "⚠\uFE0F Substitua a cada 6–12 meses ou se aparecerem rachaduras.\n\n" +
                        "\uD83D\uDCA1 Após limpar, faça um jato de teste antes de usar.\n\n", R.drawable.inalador_limpo_card));
    }

    private void adicionarArtigo(final Artigo artigo) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View itemView = inflater.inflate(R.layout.item_artigo_educativo, binding.containerArtigos, false);

        ImageView artigoImagem = itemView.findViewById(R.id.artigo_imagem);
        TextView artigoTitulo = itemView.findViewById(R.id.artigo_titulo);
        TextView artigoResumo = itemView.findViewById(R.id.artigo_resumo);
        Button artigoBtnLer = itemView.findViewById(R.id.artigo_btn_ler);

        artigoImagem.setImageResource(artigo.imagemResId);
        artigoTitulo.setText(artigo.titulo);
        artigoResumo.setText(artigo.resumo);

        artigoBtnLer.setOnClickListener(v -> showArtigoDialog(artigo.titulo, artigo.conteudo));

        binding.containerArtigos.addView(itemView);
    }

    private void showArtigoDialog(String titulo, String conteudo) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_artigo_educativo, null);
        TextView dialogTitulo = dialogView.findViewById(R.id.dialog_titulo);
        TextView dialogConteudo = dialogView.findViewById(R.id.dialog_conteudo);
        Button dialogBtnFechar = dialogView.findViewById(R.id.dialog_btn_fechar);
        dialogTitulo.setText(titulo);
        dialogConteudo.setText(conteudo);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        final AlertDialog alertDialog = builder.create();
        dialogBtnFechar.setOnClickListener(v_fechar -> alertDialog.dismiss());
        alertDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
