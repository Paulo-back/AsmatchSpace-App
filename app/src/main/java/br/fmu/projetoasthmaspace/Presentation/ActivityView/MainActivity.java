package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import br.fmu.projetoasthmaspace.Core.Navegation.NavegacaoCallback;
import br.fmu.projetoasthmaspace.Core.Session.UserServiceHelper;
import br.fmu.projetoasthmaspace.Core.Domain.Cliente.DadosDetalhamentoCliente;
import br.fmu.projetoasthmaspace.Core.Session.UserSessionManager;
import br.fmu.projetoasthmaspace.Presentation.Fragment.DiarioSintomasFragment;
import br.fmu.projetoasthmaspace.Presentation.Fragment.EducativoFragment;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiClient;
import br.fmu.projetoasthmaspace.databinding.ActivityMainBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements NavegacaoCallback {

    ActivityMainBinding binding;

    // Fragments mantidos vivos — nunca recriados ao trocar de aba
    private final TelaInicialFragment  fragHome      = new TelaInicialFragment();
    private final LembretesActivity    fragLembretes = new LembretesActivity();
    private final TarefasActivity      fragTarefas   = new TarefasActivity();
    private final DiarioSintomasFragment fragDiario  = new DiarioSintomasFragment();
    private final EducativoFragment    fragEducativo  = new EducativoFragment();

    private Fragment fragAtivo;

    // -------------------------------------------------------------------------
    // Legado — lista estática usada em outras partes do app
    // -------------------------------------------------------------------------
    public static class Lembrete {
        public String titulo;
        public String horario;
        public Date data;
        public boolean concluida;

        public Lembrete(String titulo, String horario, Date data) {
            this.titulo   = titulo;
            this.horario  = horario;
            this.data     = data;
            this.concluida = false;
        }
    }

    public static List<Lembrete> listaDeLembretes = new ArrayList<>();

    // -------------------------------------------------------------------------
    // onCreate
    // -------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean tecladoAberto = ime.bottom > 0;

            // 1) Esconde a bottom nav enquanto o teclado está aberto — libera ~150px
            binding.bottomNavigationView.setVisibility(
                    tecladoAberto ? View.GONE : View.VISIBLE);

            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));

            // 2) Rola até o campo focado depois que o layout se ajustar
            if (tecladoAberto) {
                View foco = getCurrentFocus();
                if (foco != null) {
                    foco.post(() -> foco.requestRectangleOnScreen(
                            new Rect(0, 0, foco.getWidth(), foco.getHeight()), false));
                }
            }

            return WindowInsetsCompat.CONSUMED;
        });
        solicitarIgnorarOtimizacaoBateria();
        tratarIntentDeNotificacao(getIntent());



        // Validação do token
        UserSessionManager session = new UserSessionManager(this);
        String token = session.getToken();

        if (token == null || token.isEmpty()) {
            redirecionarParaLogin();
            return;
        }

        ApiClient.getApiService(this).getMeuPerfil().enqueue(new Callback<DadosDetalhamentoCliente>() {
            @Override
            public void onResponse(Call<DadosDetalhamentoCliente> call,
                                   Response<DadosDetalhamentoCliente> response) {
                if (!response.isSuccessful()) {
                    session.clear(MainActivity.this);
                    redirecionarParaLogin();
                }
            }
            @Override
            public void onFailure(Call<DadosDetalhamentoCliente> call, Throwable t) {
                Log.w("MainActivity", "Sem conexão para validar token: " + t.getMessage());
            }
        });

        if (listaDeLembretes.isEmpty()) preencherLembretesExemplo();

        // Adiciona todos os fragments de uma vez, escondendo os que não são o inicial
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.add(R.id.frameLayout, fragHome,      "HOME");
        ft.add(R.id.frameLayout, fragLembretes, "LEMBRETES");
        ft.add(R.id.frameLayout, fragTarefas,   "TAREFAS");
        ft.add(R.id.frameLayout, fragDiario,    "DIARIO");
        ft.add(R.id.frameLayout, fragEducativo, "EDUCATIVO");
        ft.hide(fragLembretes);
        ft.hide(fragTarefas);
        ft.hide(fragDiario);
        ft.hide(fragEducativo);
        ft.commit();
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                carregarFotoPerfilToolbar();
            }
        });

        fragAtivo = fragHome;

        // Perfil — ainda usa replace pois é uma tela separada
        binding.toolbarPerfilContainer.setOnClickListener(v -> {
            Log.d("MainActivity", "perfil clicado");
            mostrarFragmentAvulso(new PerfilActivity());
            binding.bottomNavigationView.getMenu().setGroupCheckable(0, true, false);
            for (int i = 0; i < binding.bottomNavigationView.getMenu().size(); i++) {
                binding.bottomNavigationView.getMenu().getItem(i).setChecked(false);
            }
            binding.bottomNavigationView.getMenu().setGroupCheckable(0, true, true);
        });

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            getSupportFragmentManager().popBackStack(null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
            int id = item.getItemId();
            if      (id == R.id.navigation_home)      mostrarFragment(fragHome);
            else if (id == R.id.navigation_lembretes) mostrarFragment(fragLembretes);
            else if (id == R.id.navigation_tarefas)   mostrarFragment(fragTarefas);
            else if (id == R.id.navigation_diario)    mostrarFragment(fragDiario);
            else if (id == R.id.navigation_educativo) mostrarFragment(fragEducativo);
            return true;
        });

        carregarNomeUsuario();
    }
    @Override
    protected void onResume() {
        super.onResume();
        carregarFotoPerfilToolbar();
    }


    private void carregarFotoPerfilToolbar() {
        String path = new UserSessionManager(this).getFotoPath();
        if (path == null) return;

        File arquivo = new File(path);
        if (!arquivo.exists()) return;

        try {
            InputStream is = getContentResolver().openInputStream(Uri.fromFile(arquivo));
            Bitmap original = BitmapFactory.decodeStream(is);

            // Remove o tint corretamente (ImageViewCompat em vez de setColorFilter)
            androidx.core.widget.ImageViewCompat.setImageTintList(
                    binding.toolbarPerfilIcon, null
            );

            binding.toolbarPerfilIcon.setImageDrawable(
                    new BitmapDrawable(getResources(), recortarCirculo(original))
            );
        } catch (Exception e) {
            Log.e("MainActivity", "Erro ao carregar foto toolbar: " + e.getMessage());
        }
    }

    // Chamado quando o app já está aberto e chega uma nova notificação
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        tratarIntentDeNotificacao(intent);
    }

    // ✅ Lê o extra e abre NotificacoesActivity se necessário
    private void tratarIntentDeNotificacao(Intent intent) {
        if (intent != null && intent.getBooleanExtra("ABRIR_NOTIFICACOES", false)) {
            intent.removeExtra("ABRIR_NOTIFICACOES"); // evita reabrir no onResume
            startActivity(new Intent(this, NotificacoesActivity.class));
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


    // -------------------------------------------------------------------------
    // Navegação
    // -------------------------------------------------------------------------

    /** Troca entre os fragments fixos usando show/hide — sem recriar. */
    private void mostrarFragment(Fragment alvo) {
        if (alvo == fragAtivo) return;
        getSupportFragmentManager()
                .beginTransaction()
                .hide(fragAtivo)
                .show(alvo)
                .commit();
        fragAtivo = alvo;
    }

    /** Para telas avulsas (Perfil) que ainda usam replace. */
    private void mostrarFragmentAvulso(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.frameLayout, fragment)
                .addToBackStack(null)
                .commit();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void carregarNomeUsuario() {
        UserSessionManager session = new UserSessionManager(this);
        String nomeCompleto = session.getNome();
        String token = session.getToken();
        Log.d("MainActivity", "getNome retornou: " + nomeCompleto);

        if (nomeCompleto != null && !nomeCompleto.trim().isEmpty()) {
            String primeiroNome = nomeCompleto.split(" ")[0];
            binding.toolbarUserName.setText("Olá, " + primeiroNome);
            binding.toolbarUserName.setVisibility(View.VISIBLE);
        } else if (token != null) {
            UserServiceHelper.buscarNomeUsuario(this, new UserServiceHelper.NomeCallback() {
                @Override
                public void onSuccess(String nome) {
                    Log.d("MainActivity", "buscarNome onSuccess: " + nome);
                    session.saveNome(nome);
                    runOnUiThread(() -> {
                        String primeiroNome = nome.split(" ")[0];
                        binding.toolbarUserName.setText("Olá, " + primeiroNome);
                        binding.toolbarUserName.setVisibility(View.VISIBLE);
                    });
                }
                @Override
                public void onError(String erro) {
                    Log.e("MainActivity", "buscarNome onError: " + erro);
                    runOnUiThread(() -> binding.toolbarUserName.setVisibility(View.GONE));
                }
            });
        }
    }

    private void preencherLembretesExemplo() {
        Date hoje = new Date();
        listaDeLembretes.add(new Lembrete("Usar Inalador Preventivo", "08:00", hoje));
        listaDeLembretes.add(new Lembrete("Medir pico de fluxo",      "08:15", hoje));
        listaDeLembretes.add(new Lembrete("Tomar antialérgico",        "12:00", hoje));
        listaDeLembretes.add(new Lembrete("Usar Inalador Preventivo",  "20:00", hoje));

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        Date ontem = cal.getTime();

        Lembrete l1 = new Lembrete("Tomar Comprimido",         "22:00", ontem); l1.concluida = true;
        Lembrete l2 = new Lembrete("Usar Inalador de Alívio",  "15:30", ontem); l2.concluida = true;
        listaDeLembretes.add(l1);
        listaDeLembretes.add(l2);
    }

    private void redirecionarParaLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void solicitarIgnorarOtimizacaoBateria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

        @Override
        public void navegarParaEducativo () {
            mostrarFragment(fragEducativo);
            binding.bottomNavigationView.setSelectedItemId(R.id.navigation_educativo);
        }
    }
