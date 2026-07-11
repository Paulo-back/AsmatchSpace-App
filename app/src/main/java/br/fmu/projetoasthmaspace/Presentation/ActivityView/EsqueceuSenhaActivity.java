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
import br.fmu.projetoasthmaspace.Core.Util.SolicitarCodigoRequest;
import br.fmu.projetoasthmaspace.Core.Util.RedefinirSenhaComCodigoRequest;
import br.fmu.projetoasthmaspace.Core.Util.MensagemResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EsqueceuSenhaActivity extends BaseActivity {

    private ViewFlipper viewFlipper;
    private EditText editEmailRecuperacao;
    private EditText editCodigoRecuperacao;
    private TextView textEmailEnviado;
    private EditText editNovaSenha, editConfirmarSenha;
    private TextView textForcaSenha;
    private View forca1, forca2, forca3, forca4;

    private ImageButton btnToggleNovaSenha, btnToggleConfirmarSenha;
    private boolean novaSenhaVisivel = false;
    private boolean confirmarSenhaVisivel = false;

    private String emailRecuperacao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esqueceu_senha);
        bindViews();
        configurarBotoes();
        configurarForcaSenha();
        configurarTogglesSenha();
    }

    private void bindViews() {
        viewFlipper              = findViewById(R.id.viewFlipper);
        editEmailRecuperacao     = findViewById(R.id.editEmailRecuperacao);
        editCodigoRecuperacao    = findViewById(R.id.editCodigoRecuperacao);
        textEmailEnviado         = findViewById(R.id.textEmailEnviado);
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
        findViewById(R.id.btnAvancarEmail).setOnClickListener(v -> solicitarCodigo());
        findViewById(R.id.textVoltarLogin).setOnClickListener(v -> finish());
        findViewById(R.id.btnAvancarCodigo).setOnClickListener(v -> avancarCodigo());
        findViewById(R.id.textVoltarEmail).setOnClickListener(v -> viewFlipper.setDisplayedChild(0));
        findViewById(R.id.textReenviarCodigo).setOnClickListener(v -> reenviarCodigo());
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

    // ---------------------------------------------------------------- passo 0: e-mail
    private void solicitarCodigo() {
        String email = editEmailRecuperacao.getText().toString().trim();
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmailRecuperacao.setError("E-mail inválido");
            return;
        }
        emailRecuperacao = email;

        ApiService api = ApiClient.getApiService(this);
        api.solicitarCodigoRecuperacao(new SolicitarCodigoRequest(email))
                .enqueue(new Callback<MensagemResponse>() {
                    @Override
                    public void onResponse(Call<MensagemResponse> call, Response<MensagemResponse> response) {
                        if (response.isSuccessful()) {
                            if (textEmailEnviado != null) {
                                textEmailEnviado.setText("Enviamos um código de 6 dígitos para "
                                        + emailRecuperacao + ". Ele expira em 15 minutos.");
                            }
                            editCodigoRecuperacao.setText("");
                            viewFlipper.setDisplayedChild(1);
                        } else if (response.code() == 429) {
                            Toast.makeText(EsqueceuSenhaActivity.this,
                                    "Aguarde um minuto antes de solicitar um novo código.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(EsqueceuSenhaActivity.this,
                                    "Não foi possível enviar o código. Tente novamente.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<MensagemResponse> call, Throwable t) {
                        Toast.makeText(EsqueceuSenhaActivity.this,
                                "Erro de conexão. Tente novamente.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------------------------------------------------------------- passo 1: código
    private void avancarCodigo() {
        String codigo = editCodigoRecuperacao.getText().toString().trim();
        if (!codigo.matches("\\d{6}")) {
            editCodigoRecuperacao.setError("Informe o código de 6 dígitos");
            return;
        }
        // O código só é validado de fato no /redefinir; aqui apenas avançamos
        viewFlipper.setDisplayedChild(2);
    }

    private void reenviarCodigo() {
        if (emailRecuperacao == null) {
            viewFlipper.setDisplayedChild(0);
            return;
        }
        ApiService api = ApiClient.getApiService(this);
        api.solicitarCodigoRecuperacao(new SolicitarCodigoRequest(emailRecuperacao))
                .enqueue(new Callback<MensagemResponse>() {
                    @Override
                    public void onResponse(Call<MensagemResponse> call, Response<MensagemResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(EsqueceuSenhaActivity.this,
                                    "Novo código enviado!", Toast.LENGTH_SHORT).show();
                        } else if (response.code() == 429) {
                            Toast.makeText(EsqueceuSenhaActivity.this,
                                    "Aguarde um minuto antes de solicitar um novo código.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(EsqueceuSenhaActivity.this,
                                    "Não foi possível reenviar. Tente novamente.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<MensagemResponse> call, Throwable t) {
                        Toast.makeText(EsqueceuSenhaActivity.this,
                                "Erro de conexão. Tente novamente.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------------------------------------------------------------- passo 2: nova senha
    private void redefinirSenha() {
        String codigo   = editCodigoRecuperacao.getText().toString().trim();
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

        RedefinirSenhaComCodigoRequest req =
                new RedefinirSenhaComCodigoRequest(emailRecuperacao, codigo, nova);

        ApiService api = ApiClient.getApiService(this);
        api.redefinirSenhaComCodigo(req).enqueue(new Callback<MensagemResponse>() {
            @Override
            public void onResponse(Call<MensagemResponse> call, Response<MensagemResponse> response) {
                if (response.isSuccessful()) {
                    viewFlipper.setDisplayedChild(3);
                } else {
                    Toast.makeText(EsqueceuSenhaActivity.this,
                            "Código inválido ou expirado. Verifique e tente novamente.",
                            Toast.LENGTH_SHORT).show();
                    viewFlipper.setDisplayedChild(1);
                }
            }
            @Override
            public void onFailure(Call<MensagemResponse> call, Throwable t) {
                Toast.makeText(EsqueceuSenhaActivity.this,
                        "Erro de conexão. Tente novamente.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- passo 3: sucesso
    private void irParaLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
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