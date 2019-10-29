package c.acdi.master.jderamaix.suaps;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class PinDialog extends DialogFragment {



    /**
     * Constructeur par défaut de la classe
     */
    public  PinDialog(){
        super();
    }


    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final MainActivity activity = (MainActivity) getActivity();
        final View view = LayoutInflater.from(activity).inflate(R.layout.dialog_pin,null);

        return new AlertDialog.Builder(activity)
                .setTitle("Code Pin")
                .setView(view)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        final EditText _text_code_pin = (EditText) view.findViewById(R.id.code_pin);
                        String code_pin = _text_code_pin.getText().toString();
                        String msg = "Erreur";
                        if( code_pin.matches("100")){ msg = "Le mot de passe a été modifié"; }else{ msg = "L'ancien code de pin est incorrect, veuillez réessayer"; }
                        dialog_msg(msg).show();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .create();

    }

    public Dialog dialog_msg(String msg) {

        final MainActivity activity = (MainActivity) getActivity();
        final View view = LayoutInflater.from(activity).inflate(R.layout.dialog_pin,null);
        //String msg = "Erreur";

        //if(code == "100"){ msg ="Correct" ; }
        return new AlertDialog.Builder(activity)
                .setTitle(msg)
                //.setView(view)
                .setPositiveButton("DONE",null)
                .create();
    }
}
