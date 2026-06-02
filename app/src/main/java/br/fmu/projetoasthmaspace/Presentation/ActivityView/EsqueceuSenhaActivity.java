package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiClient;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiService;
import br.fmu.projetoasthmaspace.Core.Domain.Log.ConsultaInfoResponse;
import br.fmu.projetoasthmaspace.Core.Domain.Log.VerificarIdentidadeRequest;
import br.fmu.projetoasthmaspace.Core.Domain.Log.VerificarIdentidadeResponse;
import br.fmu.projetoasthmaspace.Core.Domain.Log.RedefinirSenhaRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EsqueceuSenhaActivity extends BaseActivity {

    private ViewFlipper viewFlipper;
    private EditText editEmailRecuperacao;
    private EditText editDataNascimento, editCpf;
    private TextView labelCpf;
    private EditText editNovaSenha, editConfirmarSenha;
    private TextView textForcaSenha;
    private View forca1, forca2, forca3, forca4;

    private ImageButton btnToggleNovaSenha, btnToggleConfirmarSenha;
    private boolean novaSenhaVisivel = false;
    private boolean confirmarSenhaVisivel = false;

    private String emailRecuperacao;
    private String tokenRedefinicao;
    private boolean clienteTemCpf = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esqueceu_senha);
        bindViews();
        configurarBotoes();
        configurarMascaraData();
        configurarForcaSenha();
        configurarTogglesSenha();
    }

    private void bindViews() {
        viewFlipper              = findViewById(R.id.viewFlipper);
        editEmailRecuperacao     = findViewById(R.id.editEmailRecuperacao);
        editDataNascimento       = findViewById(R.id.editDataNascimento);
        editCpf                  = findViewById(R.id.editCpf);
        labelCpf                 = findViewById(R.id.labelCpf);
        editNovaSenha            = findViewById(R.id.editNovaSenha);
        editConfirmarSenha       = findViewById(R.id.editConfirmarSenha);
        textForcaSenha           = findViewById(R.id.textForcaSenha);
        forca1                   = findViewById(R.id.forca1);
        forca2                   = findViewById(R.id.forca2);
        forca3                   = findViewById(R.id.forca3);
        forca4                   = findViewById(R.id.forca4);
        btnToggleNovaSenha       = findViewById(R.id.btnToggleNovaSenha);
        btnToggleConfirmarSenha  = findViewById(R.id.btnToggleConfirmarSenha);
    }

    private void configurarBotoes() {
        findViewById(R.id.btnAvancarEmail).setOnClickListener(v -> avancarEmail());
        findViewById(R.id.textVoltarLogin).setOnClickListener(v -> finish());
        findViewById(R.id.btnConfirmarIdentidade).setOnClickListener(v -> confirmarIdentidade());
        findViewById(R.id.textVoltarEmail).setOnClickListener(v -> viewFlipper.showPrevious());
        findViewById(R.id.btnRedefinirSenha).setOnClickListener(v -> redefinirSenha());
        findViewById(R.id.btnIrParaLogin).setOnClickListener(v -> irParaLogin());
    }

    private void configurarTogglesSenha() {
        btnToggleNovaSenha.setOnClickListener(v -> {
            novaSenhaVisivel = !novaSenhaVisivel;
            editNovaSenha.setTransformationMethod(novaSenhaVisivel
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            btnToggleNovaSenha.setImageResource(novaSenhaVisivel
                    ? R.drawable.ic_visibility
                    : R.drawable.ic_visibility_off);
            editNovaSenha.setSelection(editNovaSenha.getText().length());
        });

        btnToggleConfirmarSenha.setOnClickListener(v -> {
            confirmarSenhaVisivel = !confirmarSenhaVisivel;
            editConfirmarSenha.setTransformationMethod(confirmarSenhaVisivel
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            btnToggleConfirmarSenha.setImageResource(confirmarSenhaVisivel
                    ? R.drawable.ic_visibility
                    : R.drawable.ic_visibility_off);
            editConfirmarSenha.setSelection(editConfirmarSenha.getText().length());
        });
    }

    // ---------------------------------------------------------------- passo 0
    private void avancarEmail() {
        String email = editEmailRecuperacao.getText().toString().trim();
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmailRecuperacao.setError("E-mail inválido");
            return;
        }
        emailRecuperacao = email;

        ApiService api = ApiClient.getApiService(this);
        api.consultarInfoRecuperacao(email).enqueue(new Callback<ConsultaInfoResponse>() {
            @Override
            public void onResponse(Call<ConsultaInfoResponse> call, Response<ConsultaInfoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    clienteTemCpf = response.body().isTemCpf();
                    int vis = clienteTemCpf ? View.VISIBLE : View.GONE;
                    labelCpf.setVisibility(vis);
                    editCpf.setVisibility(vis);
                    viewFlipper.showNext();
                } else {
                    Toast.makeText(EsqueceuSenhaActivity.this,
                            "Não foi possível localizar este e-mail.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ConsultaInfoResponse> call, Throwable t) {
                Toast.makeText(EsqueceuSenhaActivity.this,
                        "Erro de conexão. Tente novamente.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- passo 1
    private void confirmarIdentidade() {
        String dataStr = editDataNascimento.getText().toString().trim();

        if (dataStr.length() != 10) {
            editDataNascimento.setError("Informe a data completa (DD/MM/AAAA)");
            return;
        }

        String dataFormatada;
        try {
            String[] partes = dataStr.split("/");
            if (partes.length != 3) throw new Exception("formato inválido");
            dataFormatada = partes[2] + "-" + partes[1] + "-" + partes[0];
        } catch (Exception e) {
            editDataNascimento.setError("Formato inválido. Use DD/MM/AAAA");
            return;
        }

        if (clienteTemCpf) {
            String cpf = editCpf.getText().toString().replaceAll("[^0-9]", "");
            if (cpf.length() != 11) {
                editCpf.setError("CPF inválido");
                return;
            }
        }

        VerificarIdentidadeRequest req = new VerificarIdentidadeRequest();
        req.setEmail(emailRecuperacao);
        req.setDataNascimento(dataFormatada);
        if (clienteTemCpf) {
            req.setCpf(editCpf.getText().toString().replaceAll("[^0-9]", ""));
        }

        ApiService api = ApiClient.getApiService(this);
        api.verificarIdentidade(req).enqueue(new Callback<VerificarIdentidadeResponse>() {
            @Override
            public void onResponse(Call<VerificarIdentidadeResponse> call,
                                   Response<VerificarIdentidadeResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tokenRedefinicao = response.body().getTokenRedefinicao();
                    viewFlipper.showNext();
                } else {
                    Toast.makeText(EsqueceuSenhaActivity.this,
                            "Dados não conferem com nosso cadastro.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<VerificarIdentidadeResponse> call, Throwable t) {
                Toast.makeText(EsqueceuSenhaActivity.this,
                        "Erro de conexão. Tente novamente.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- passo 2
    private void redefinirSenha() {
        String nova     = editNovaSenha.getText().toString();
        String confirma = editConfirmarSenha.getText().toString();

        if (nova.length() < 8) {
            editNovaSenha.setError("Mínimo 8 caracteres");
            return;
        }
        if (!nova.equals(confirma)) {
            editConfirmarSenha.setError("As senhas não coincidem");
            return;
        }

        RedefinirSenhaRequest req = new RedefinirSenhaRequest();
        req.setTokenRedefinicao(tokenRedefinicao);
        req.setNovaSenha(nova);

        ApiService api = ApiClient.getApiService(this);
        api.redefinirSenha(req).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    viewFlipper.showNext();
                } else {
                    Toast.makeText(EsqueceuSenhaActivity.this,
                            "Token expirado. Reinicie o processo.", Toast.LENGTH_SHORT).show();
                    viewFlipper.setDisplayedChild(0);
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(EsqueceuSenhaActivity.this,
                        "Erro de conexão. Tente novamente.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- passo 3
    private void irParaLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // ---------------------------------------------------- máscara DD/MM/AAAA
    private void configurarMascaraData() {
        editDataNascimento.addTextChangedListener(new TextWatcher() {
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
                editDataNascimento.setText(formatted.toString());
                editDataNascimento.setSelection(formatted.length());
                isUpdating = false;
            }
        });
    }

    // --------------------------------------------------------- força da senha
    private void configurarForcaSenha() {
        editNovaSenha.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { atualizarForca(s.toString()); }
        });
    }

    private void atualizarForca(String senha) {
        int pontos = 0;
        if (senha.length() >= 8)                                                pontos++;
        if (senha.matches(".*[A-Z].*"))                                         pontos++;
        if (senha.matches(".*[0-9].*"))                                         pontos++;
        if (senha.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) pontos++;

        int corAtiva   = 0xFF4DD9B8;
        int corInativa = 0x33FFFFFF;
        forca1.setBackgroundColor(pontos >= 1 ? corAtiva : corInativa);
        forca2.setBackgroundColor(pontos >= 2 ? corAtiva : corInativa);
        forca3.setBackgroundColor(pontos >= 3 ? corAtiva : corInativa);
        forca4.setBackgroundColor(pontos >= 4 ? corAtiva : corInativa);

        String[] labels = {"", "Fraca", "Média", "Forte", "Muito forte"};
        textForcaSenha.setText(senha.isEmpty() ? "" : "Força: " + labels[pontos]);
    }
}