package br.fmu.projetoasthmaspace.Core.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ValidadorDataNascimento {

    private static final TimeZone TZ_SP = TimeZone.getTimeZone("America/Sao_Paulo");
    private static final int IDADE_MAXIMA = 120;


    public static String validar(String dataExibida) {
        if (dataExibida == null || dataExibida.length() != 10) {
            return "Data de nascimento incompleta.";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        sdf.setTimeZone(TZ_SP);
        sdf.setLenient(false); // rejeita 31/02, mês 13, dia 99 etc.

        Date data;
        try {
            data = sdf.parse(dataExibida);
        } catch (ParseException e) {
            return "Data de nascimento inválida.";
        }

        Calendar hoje = Calendar.getInstance(TZ_SP);

        Calendar nascimento = Calendar.getInstance(TZ_SP);
        nascimento.setTime(data);

        if (nascimento.after(hoje)) {
            return "Data de nascimento não pode ser futura.";
        }

        Calendar minimo = Calendar.getInstance(TZ_SP);
        minimo.add(Calendar.YEAR, -IDADE_MAXIMA);
        if (nascimento.before(minimo)) {
            return "Data de nascimento inválida.";
        }

        return null;
    }
}