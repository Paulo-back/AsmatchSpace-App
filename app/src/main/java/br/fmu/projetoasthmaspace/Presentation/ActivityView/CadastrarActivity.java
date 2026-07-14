package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import br.fmu.projetoasthmaspace.Core.Util.ValidadorDataNascimento;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.databinding.ActivityCadastrarBinding;

public class CadastrarActivity extends BaseActivity implements AdapterView.OnItemSelectedListener {

    private ActivityCadastrarBinding binding;

    // Controle de visibilidade dos campos de senha
    private boolean senhaVisivel = false;
    private boolean confirmarSenhaVisivel = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCadastrarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.spinnerSexo.setOnItemSelectedListener(this);

        binding.btnVoltar.setOnClickListener(v -> {
            startActivity(new Intent(CadastrarActivity.this, LoginActivity.class));
            finish();
        });

        configurarMascaraData();
        configurarToggleSenha();

        binding.btnContinuar.setOnClickListener(v -> {
            String nomeCompleto   = binding.editTextNomeCompleto.getText().toString();
            String email          = binding.editTextEmail.getText().toString();
            String senha          = binding.editTextCriarSenha.getText().toString();
            String confirmarSenha = binding.editTextConfirmarSenha.getText().toString();
            String cpf            = binding.editTextCpf.getText().toString();
            String telefone       = binding.editTextTelefone.getText().toString();
            String dataNascimento = binding.editTextDataNascimento.getText().toString();
            String sexo           = binding.spinnerSexo.getSelectedItem().toString();

            if (nomeCompleto.isEmpty() || email.isEmpty() || senha.isEmpty() ||
                    confirmarSenha.isEmpty() || dataNascimento.isEmpty() || sexo.isEmpty()) {

            }

            if (sexo.equals("Selecione")) {
                Toast.makeText(this, "Selecione um sexo.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Valida se as senhas conferem
            if (!senha.equals(confirmarSenha)) {
                binding.editTextConfirmarSenha.setError("As senhas não coincidem");
                binding.editTextConfirmarSenha.requestFocus();
                Toast.makeText(this, "As senhas não coincidem.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Valida (data real, não futura, máx. 120 anos)
            String erroData = ValidadorDataNascimento.validar(dataNascimento);
            if (erroData != null) {
                binding.editTextDataNascimento.setError(erroData);
                binding.editTextDataNascimento.requestFocus();
                Toast.makeText(this, erroData, Toast.LENGTH_SHORT).show();
                return;
            }

            // Converte DD/MM/AAAA → yyyy-MM-dd para o backend
            String[] partes = dataNascimento.split("/");
            String dataFormatada = partes[2] + "-" + partes[1] + "-" + partes[0];

            // Envia apenas a senha original, não a confirmação
            Intent intent = new Intent(CadastrarActivity.this, InfAdicionaisActivity.class);
            intent.putExtra("USER_NAME", nomeCompleto);
            intent.putExtra("email", email);
            intent.putExtra("cpf", cpf);
            intent.putExtra("telefone", telefone);
            intent.putExtra("dataNascimento", dataFormatada);
            intent.putExtra("senha", senha);
            intent.putExtra("sexo", sexo);
            startActivity(intent);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void configurarToggleSenha() {
        // Olhinho — campo "Criar Senha"
        binding.btnToggleSenha.setOnClickListener(v -> {
            senhaVisivel = !senhaVisivel;
            if (senhaVisivel) {
                binding.editTextCriarSenha.setTransformationMethod(
                        HideReturnsTransformationMethod.getInstance());
                binding.btnToggleSenha.setImageResource(R.drawable.ic_visibility);
            } else {
                binding.editTextCriarSenha.setTransformationMethod(
                        PasswordTransformationMethod.getInstance());
                binding.btnToggleSenha.setImageResource(R.drawable.ic_visibility_off);
            }
            // Mantém cursor no fim do texto
            binding.editTextCriarSenha.setSelection(
                    binding.editTextCriarSenha.getText().length());
        });

        // Olhinho — campo "Confirmar Senha"
        binding.btnToggleConfirmarSenha.setOnClickListener(v -> {
            confirmarSenhaVisivel = !confirmarSenhaVisivel;
            if (confirmarSenhaVisivel) {
                binding.editTextConfirmarSenha.setTransformationMethod(
                        HideReturnsTransformationMethod.getInstance());
                binding.btnToggleConfirmarSenha.setImageResource(R.drawable.ic_visibility);
            } else {
                binding.editTextConfirmarSenha.setTransformationMethod(
                        PasswordTransformationMethod.getInstance());
                binding.btnToggleConfirmarSenha.setImageResource(R.drawable.ic_visibility_off);
            }
            binding.editTextConfirmarSenha.setSelection(
                    binding.editTextConfirmarSenha.getText().length());
        });
    }

    private void configurarMascaraData() {
        binding.editTextDataNascimento.addTextChangedListener(new TextWatcher() {
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
                binding.editTextDataNascimento.setText(formatted.toString());
                binding.editTextDataNascimento.setSelection(formatted.length());
                isUpdating = false;
            }
        });
    }

    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {}
    @Override public void onNothingSelected(AdapterView<?> parent) {}
}