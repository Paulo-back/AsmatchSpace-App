package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.shimmer.ShimmerFrameLayout;

import java.io.File;
import java.io.InputStream;
import java.util.Objects;

import br.fmu.projetoasthmaspace.Core.Session.UserSessionManager;
import br.fmu.projetoasthmaspace.Core.Util.ValidadorDataNascimento;
import br.fmu.projetoasthmaspace.Data.Service.ViaCep.ApiViaCep;
import br.fmu.projetoasthmaspace.Core.Util.AtualizarRequest;
import br.fmu.projetoasthmaspace.Core.Domain.Cliente.DadosDetalhamentoCliente;
import br.fmu.projetoasthmaspace.Core.Domain.Endereco.Endereco;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiClient;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiService;
import br.fmu.projetoasthmaspace.Data.Service.ViaCep.ViaCepResponse;
import br.fmu.projetoasthmaspace.databinding.ActivityInformacoesPessoaisBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InformacoesPessoaisActivity extends BaseActivity {

    private ActivityInformacoesPessoaisBinding binding;
    private ApiService api;
    private DadosDetalhamentoCliente dadosAtuais;
    private boolean carregandoDados = false;

    private String snapNome, snapTelefone, snapCpf, snapDataNascimento, snapSexo;
    private String snapMedicamentos, snapProblemaResp, snapEmergencia;
    private String snapCep, snapLogradouro, snapNumero, snapComplemento, snapBairro, snapCidade, snapUf;

    // ── Skeleton ──────────────────────────────────────────────────────────────
    private ShimmerFrameLayout shimmerLayout;
    private boolean skeletonInflado = false;
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInformacoesPessoaisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        api = ApiClient.getApiService(getApplicationContext());

        // Mostra skeleton antes de qualquer chamada
        mostrarSkeleton();
        carregarFotoPerfil();
        carregarDadosBackend();
        configurarMascaraData();

        binding.edtCEP.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !carregandoDados) {
                String cep = binding.edtCEP.getText().toString().trim();
                if (cep.matches("\\d{8}")) buscarCep(cep);
            }
        });

        binding.btnVoltar.setOnClickListener(v -> finish());
        binding.btnSalvar.setOnClickListener(v -> salvarAlteracoes());

    }

    private void carregarFotoPerfil() {
        String path = new UserSessionManager(this).getFotoPath();
        if (path == null) return;

        File arquivo = new File(path);
        if (!arquivo.exists()) return;

        try {
            InputStream is = getContentResolver().openInputStream(Uri.fromFile(arquivo));
            Bitmap original = BitmapFactory.decodeStream(is);
            binding.imgPerfil.setImageDrawable(
                    new BitmapDrawable(getResources(), recortarCirculo(original))
            );
        } catch (Exception e) {
            Log.e("INFO_PESSOAIS", "Erro ao carregar foto: " + e.getMessage());
        }
    }

    private Bitmap recortarCirculo(Bitmap bitmap) {
        int tamanho = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap saida = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(saida);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(
                Bitmap.createScaledBitmap(bitmap, tamanho, tamanho, true),
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(tamanho / 2f, tamanho / 2f, tamanho / 2f, paint);
        return saida;
    }


    // ── Skeleton helpers ──────────────────────────────────────────────────────

    private void mostrarSkeleton() {
        if (!skeletonInflado) {
            shimmerLayout = (ShimmerFrameLayout) binding.skeletonStub.inflate();
            skeletonInflado = true;
        }
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        binding.scrollFormulario.setVisibility(View.GONE);
    }

    private void esconderSkeleton() {
        if (shimmerLayout != null) {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
        }
        // Esconde também o ScrollView container do skeleton
        binding.scrollSkeleton.setVisibility(View.GONE);
        binding.scrollFormulario.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void configurarMascaraData() {
        binding.edtDataNascimento.addTextChangedListener(new TextWatcher() {
            private boolean isUpdating = false;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) return;
                String digits = s.toString().replaceAll("[^0-9]", "");
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length() && i < 8; i++) {
                    if (i == 2 || i == 4) formatted.append("/");
                    formatted.append(digits.charAt(i));
                }
                isUpdating = true;
                binding.edtDataNascimento.setText(formatted.toString());
                binding.edtDataNascimento.setSelection(formatted.length());
                isUpdating = false;
            }
        });
    }

    private void carregarDadosBackend() {
        api.getMeuPerfil().enqueue(new Callback<DadosDetalhamentoCliente>() {
            @Override
            public void onResponse(Call<DadosDetalhamentoCliente> call,
                                   Response<DadosDetalhamentoCliente> response) {
                if (response.isSuccessful() && response.body() != null) {
                    dadosAtuais = response.body();
                    atualizarUI(dadosAtuais);
                    tirarSnapshot();
                    esconderSkeleton(); // ← só após preencher o formulário
                } else {
                    Toast.makeText(InformacoesPessoaisActivity.this,
                            "Erro ao buscar dados", Toast.LENGTH_SHORT).show();
                    esconderSkeleton(); // ← evita tela travada
                }
            }

            @Override
            public void onFailure(Call<DadosDetalhamentoCliente> call, Throwable t) {
                Log.e("INFO_PESSOAIS", "Falha: " + t.getMessage());
                Toast.makeText(InformacoesPessoaisActivity.this,
                        "Falha na comunicação", Toast.LENGTH_SHORT).show();
                esconderSkeleton(); // ← evita tela travada
            }
        });
    }

    private void tirarSnapshot() {
        snapNome           = binding.edtNome.getText().toString().trim();
        snapTelefone       = binding.edtTelefone.getText().toString().trim();
        snapCpf            = binding.edtCPF.getText().toString().trim();
        snapDataNascimento = binding.edtDataNascimento.getText().toString().trim();
        snapSexo           = binding.spinnerSexo.getSelectedItem().toString();
        snapMedicamentos   = binding.edtMedicamentos.getText().toString().trim();
        snapProblemaResp   = binding.edtProblemaResp.getText().toString().trim();
        snapEmergencia     = binding.edtEmergencia.getText().toString().trim();
        snapCep            = binding.edtCEP.getText().toString().trim();
        snapLogradouro     = binding.edtLogradouro.getText().toString().trim();
        snapNumero         = binding.edtNumero.getText().toString().trim();
        snapComplemento    = binding.edtComplemento.getText().toString().trim();
        snapBairro         = binding.edtBairro.getText().toString().trim();
        snapCidade         = binding.edtCidade.getText().toString().trim();
        snapUf             = binding.edtUF.getText().toString().trim();
    }

    private boolean houveAlteracao() {
        return !eq(binding.edtNome.getText().toString().trim(),           snapNome)
                || !eq(binding.edtTelefone.getText().toString().trim(),       snapTelefone)
                || !eq(binding.edtCPF.getText().toString().trim(),            snapCpf)
                || !eq(binding.edtDataNascimento.getText().toString().trim(), snapDataNascimento)
                || !eq(binding.spinnerSexo.getSelectedItem().toString(),      snapSexo)
                || !eq(binding.edtMedicamentos.getText().toString().trim(),   snapMedicamentos)
                || !eq(binding.edtProblemaResp.getText().toString().trim(),   snapProblemaResp)
                || !eq(binding.edtEmergencia.getText().toString().trim(),     snapEmergencia)
                || !eq(binding.edtCEP.getText().toString().trim(),            snapCep)
                || !eq(binding.edtLogradouro.getText().toString().trim(),     snapLogradouro)
                || !eq(binding.edtNumero.getText().toString().trim(),         snapNumero)
                || !eq(binding.edtComplemento.getText().toString().trim(),    snapComplemento)
                || !eq(binding.edtBairro.getText().toString().trim(),         snapBairro)
                || !eq(binding.edtCidade.getText().toString().trim(),         snapCidade)
                || !eq(binding.edtUF.getText().toString().trim(),             snapUf);
    }

    private boolean eq(String a, String b) { return Objects.equals(a, b); }

    private void atualizarUI(DadosDetalhamentoCliente d) {
        carregandoDados = true;

        binding.edtNome.setText(safe(d.getNome()));
        binding.txtEmail.setText(safe(d.getEmail()));
        binding.edtTelefone.setText(safe(d.getTelefone()));

        String cpfAtual = safe(d.getCpf());
        binding.edtCPF.setText(cpfAtual);
        if (!cpfAtual.isEmpty()) {
            binding.edtCPF.setEnabled(false);
            binding.edtCPF.setTextColor(0xFF8EADD4);
        } else {
            binding.edtCPF.setEnabled(true);
            binding.edtCPF.setTextColor(getColor(android.R.color.white));
        }

        String dataBanco = safe(d.getDataNascimento());
        if (dataBanco.contains("-")) {
            try {
                String[] p = dataBanco.split("-");
                dataBanco = p[2] + "/" + p[1] + "/" + p[0];
            } catch (Exception ignored) {}
        }
        binding.edtDataNascimento.setText(dataBanco);

        binding.edtMedicamentos.setText(safe(d.getMedicamentos()));
        binding.edtProblemaResp.setText(safe(d.getProblema_respiratorio()));
        binding.edtEmergencia.setText(safe(d.getContatoEmergencia()));

        String sexo = safe(d.getSexo());
        String[] opcoesSexo = getResources().getStringArray(R.array.spinner_sexo);
        for (int i = 0; i < opcoesSexo.length; i++) {
            if (opcoesSexo[i].equalsIgnoreCase(sexo)) {
                binding.spinnerSexo.setSelection(i);
                break;
            }
        }

        if (d.getEndereco() != null) {
            binding.edtCEP.setText(safe(d.getEndereco().getCep()));
            binding.edtLogradouro.setText(safe(d.getEndereco().getLogradouro()));
            binding.edtNumero.setText(safe(d.getEndereco().getNumero()));
            binding.edtComplemento.setText(safe(d.getEndereco().getComplemento()));
            binding.edtBairro.setText(safe(d.getEndereco().getBairro()));
            binding.edtCidade.setText(safe(d.getEndereco().getCidade()));
            binding.edtUF.setText(safe(d.getEndereco().getUf()));
        }

        carregandoDados = false;
    }

    private void salvarAlteracoes() {
        if (dadosAtuais == null) return;

        if (!houveAlteracao()) {
            Toast.makeText(this, "Nenhuma alteração detectada.", Toast.LENGTH_SHORT).show();
            return;
        }

        String cep         = nullIfEmpty(binding.edtCEP.getText().toString());
        String logradouro  = nullIfEmpty(binding.edtLogradouro.getText().toString());
        String bairro      = nullIfEmpty(binding.edtBairro.getText().toString());
        String cidade      = nullIfEmpty(binding.edtCidade.getText().toString());
        String uf          = nullIfEmpty(binding.edtUF.getText().toString());
        String numero      = nullIfEmpty(binding.edtNumero.getText().toString());
        String complemento = nullIfEmpty(binding.edtComplemento.getText().toString());

        boolean algumPreenchido = cep != null || logradouro != null || bairro != null
                || cidade != null || uf != null;
        boolean todosObrigatoriosPreenchidos = cep != null && logradouro != null
                && bairro != null && cidade != null && uf != null;

        if (algumPreenchido && !todosObrigatoriosPreenchidos) {
            Toast.makeText(this, "Preencha todos os campos obrigatórios do endereço",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (cep != null && !cep.matches("\\d{8}")) {
            Toast.makeText(this, "CEP deve ter 8 dígitos sem traço", Toast.LENGTH_SHORT).show();
            return;
        }

        String dataExibida  = binding.edtDataNascimento.getText().toString().trim();
        String dataFormatada = null;
        if (!dataExibida.isEmpty()) {
            String erroData = ValidadorDataNascimento.validar(dataExibida);
            if (erroData != null) {
                binding.edtDataNascimento.setError(erroData);
                binding.edtDataNascimento.requestFocus();
                Toast.makeText(this, erroData, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] p = dataExibida.split("/");
            dataFormatada = p[2] + "-" + p[1] + "-" + p[0];
        }

        binding.btnSalvar.setEnabled(false);
        binding.btnSalvar.setText("Salvando...");

        String sexoSelecionado = binding.spinnerSexo.getSelectedItem().toString();

        AtualizarRequest request = new AtualizarRequest();
        request.nome                  = nullIfEmpty(binding.edtNome.getText().toString());
        request.telefone              = nullIfEmpty(binding.edtTelefone.getText().toString());
        request.sexo                  = nullIfEmpty(sexoSelecionado);
        request.dataNascimento        = dataFormatada;
        request.medicamentos          = nullIfEmpty(binding.edtMedicamentos.getText().toString());
        request.contatoEmergencia     = nullIfEmpty(binding.edtEmergencia.getText().toString());
        request.problema_respiratorio = nullIfEmpty(binding.edtProblemaResp.getText().toString());

        String cpfAtual = safe(dadosAtuais.getCpf());
        request.cpf = cpfAtual.isEmpty()
                ? nullIfEmpty(binding.edtCPF.getText().toString())
                : null;

        if (algumPreenchido) {
            Endereco end = new Endereco();
            end.setCep(cep);
            end.setLogradouro(logradouro);
            end.setNumero(numero);
            end.setComplemento(complemento);
            end.setBairro(bairro);
            end.setCidade(cidade);
            end.setUf(uf);
            request.endereco = end;
        } else {
            request.endereco = null;
        }

        api.atualizarPerfil(request).enqueue(new Callback<DadosDetalhamentoCliente>() {
            @Override
            public void onResponse(Call<DadosDetalhamentoCliente> call,
                                   Response<DadosDetalhamentoCliente> response) {
                binding.btnSalvar.setEnabled(true);
                binding.btnSalvar.setText("Salvar Alterações");

                if (response.isSuccessful() && response.body() != null) {
                    dadosAtuais = response.body();
                    atualizarUI(dadosAtuais);
                    tirarSnapshot();
                    Toast.makeText(InformacoesPessoaisActivity.this,
                            "✓ Alterações salvas!", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e("INFO_PESSOAIS", "Erro HTTP: " + response.code());
                    Toast.makeText(InformacoesPessoaisActivity.this,
                            "Erro ao salvar alterações", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DadosDetalhamentoCliente> call, Throwable t) {
                binding.btnSalvar.setEnabled(true);
                binding.btnSalvar.setText("Salvar Alterações");
                Log.e("INFO_PESSOAIS", "Falha: " + t.getMessage());
                Toast.makeText(InformacoesPessoaisActivity.this,
                        "Falha na comunicação", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buscarCep(String cep) {
        ApiViaCep.getService().buscarCep(cep).enqueue(new Callback<ViaCepResponse>() {
            @Override
            public void onResponse(Call<ViaCepResponse> call, Response<ViaCepResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(InformacoesPessoaisActivity.this,
                            "CEP inválido ou não encontrado", Toast.LENGTH_SHORT).show();
                    limparCamposCep();
                    return;
                }
                ViaCepResponse dados = response.body();
                if (Boolean.TRUE.equals(dados.erro)) {
                    Toast.makeText(InformacoesPessoaisActivity.this,
                            "CEP não encontrado", Toast.LENGTH_SHORT).show();
                    limparCamposCep();
                    return;
                }
                binding.edtLogradouro.setText(dados.logradouro);
                binding.edtBairro.setText(dados.bairro);
                binding.edtCidade.setText(dados.localidade);
                binding.edtUF.setText(dados.uf);
                if (dados.complemento != null && !dados.complemento.isEmpty())
                    binding.edtComplemento.setText(dados.complemento);
                binding.edtNumero.requestFocus();
            }

            @Override
            public void onFailure(Call<ViaCepResponse> call, Throwable t) {
                Toast.makeText(InformacoesPessoaisActivity.this,
                        "Erro ao buscar CEP.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void limparCamposCep() {
        binding.edtLogradouro.setText("");
        binding.edtBairro.setText("");
        binding.edtCidade.setText("");
        binding.edtUF.setText("");
        binding.edtNumero.setText("");
        binding.edtComplemento.setText("");
    }

    private String nullIfEmpty(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "" : s;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dadosAtuais = null;
        binding = null;
        api = null;
    }
}