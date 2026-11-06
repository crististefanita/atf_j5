package ro.rentzlab.app;

import ro.rentzlab.ai.*;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import ro.rentzlab.core.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import ro.rentzlab.app.lifecycle.SessionController;

/** Native Rentz presentation: the model and player implementations are shared with offline components. */
public final class MainActivity extends Activity {
    private static final int FELT=Color.rgb(23,79,60),CREAM=Color.rgb(241,230,200),GOLD=Color.rgb(220,181,105);
    private SessionController controller;
    private LinearLayout page;private String screen="MENU",problem="";
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(FELT);getWindow().setNavigationBarColor(FELT);
        controller=new SessionController(((RentzApplication)getApplication()).sessions(),Executors.newSingleThreadExecutor(),this::runOnUiThread,new SessionController.Listener(){
            @Override public void ready(){problem="";render();}
            @Override public void failed(Exception error){showFailure(error);}
        });
        try{controller.activate();}catch(Exception error){problem="Partida salvată nu poate fi reluată. Datele au fost păstrate.";}
        menu();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void base(String title){var scroll=new ScrollView(this);scroll.setFillViewport(true);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(14),dp(14),dp(14),dp(24));page.setBackgroundColor(FELT);scroll.addView(page);setContentView(scroll);text(title,24);}
    private void text(String value,int size){var t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(CREAM);t.setPadding(dp(4),dp(7),dp(4),dp(7));page.addView(t);}
    private Button button(String label,Runnable action){var b=new Button(this);b.setText(label);b.setAllCaps(false);b.setContentDescription(label);b.setMinHeight(dp(48));b.setOnClickListener(v->{if(!controller.isBusy())action.run();});page.addView(b);return b;}
    private void menu(){screen="MENU";base("Rentz Lab");text("Opt sub-jocuri · levate și secvențe",17);if(!problem.isEmpty())text(problem,15);
        if(controller.session()!=null)button("Continuă partida",()->act(controller::resume));
        text("Jucători",16);var players=new Spinner(this);players.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"3 jucători","4 jucători","5 jucători","6 jucători"}));players.setSelection(1);players.setBackgroundColor(CREAM);page.addView(players);
        text("Adversari",16);var difficulty=new Spinner(this);difficulty.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Normal","Hard"}));difficulty.setSelection(1);difficulty.setBackgroundColor(CREAM);page.addView(difficulty);
        button("Partidă nouă",()->{int count=players.getSelectedItemPosition()+3;var mode=difficulty.getSelectedItemPosition()==0?RentzPlayer.Difficulty.NORMAL:RentzPlayer.Difficulty.HARD;Runnable start=()->act(()->controller.newGame(count,System.currentTimeMillis(),mode));if(controller.session()==null)start.run();else new AlertDialog.Builder(this).setMessage("Începi o partidă nouă?").setNegativeButton("Renunță",null).setPositiveButton("Începe",(d,w)->start.run()).show();});
        text("Profil de laborator",17);text("Levată −10 · damă −25 · caro −20 · K♥ −100\nWhist +10 · 10♣ +100 · Rentz 300 / 200 / 100 / 0",14);button("Cum se joacă",this::help);publish();
    }
    private void help(){screen="HELP";base("Cum se joacă");text("Fiecare jucător alege fiecare sub-joc o dată. Alegătorul deschide mâna.",17);text("În jocurile cu levate urmezi culoarea cerută. Fără culoare, arunci orice carte; nu există atu. Cea mai mare carte a culorii cerute câștigă levata.",17);text("Negative: levate, dame, carouri, K♥ și Totale. Pozitive: Whist și 10♣. În Totale, dama de caro se punctează în ambele categorii.",17);text("Rentz: deschizi culorile cu Q / J / 10 / 9 pentru 3 / 4 / 5 / 6 jucători. Extinzi cu rang vecin; asul dă încă o mutare, cartea minimă sare următorul jucător. Pas numai când nu ai continuare.",17);text("Pe nevăzute: alegi înainte de a vedea cărțile, cu punctele mâinii dublate pentru toți. Nu este permis când îți mai rămâne un singur sub-joc. Acesta este profilul de laborator al aplicației locale.",16);button("Meniu principal",this::menu);publish();}
    private void act(BooleanSupplier command){try{if(command.getAsBoolean()&&controller.isBusy()){base("Se pregătește masa…");text("Adversarii joacă",18);}}catch(Exception error){showFailure(error);}}
    private void showFailure(Exception error){android.util.Log.e("RentzLab","Action failed",error);problem="Acțiunea nu a putut fi încheiată. Poți relua partida din meniu.";menu();}
    private void apply(GameSession.Command command){act(()->controller.apply(command));}
    private void render(){if(isFinishing()||isDestroyed())return;var session=controller.session();if(session==null){menu();return;}screen="GAME";var v=session.view();base("Rentz · mâna "+v.handNumber()+" / "+v.players()*8);text((session.difficulty()==RentzPlayer.Difficulty.HARD?"Hard":"Normal")+" · profil laborator",14);
        var names=new StringBuilder();for(int seat=0;seat<v.players();seat++)names.append(seat==0?"Tu":"AI "+seat).append(": ").append(v.totals().get(seat)).append(seat==v.players()-1?"":"   ·   ");text(names.toString(),16);
        if(v.phase()==RentzMatch.Phase.CHOOSE){text("Alegi sub-jocul",21);if(!v.revealed())button("Vezi cărțile",()->apply(new GameSession.Command(GameSession.Action.REVEAL,0,null,null,false)));else showCards(v,false);
            boolean blind=session.blindChoiceAvailable();
            for(var contract:Contract.values())if(v.available().contains(contract))button("Alege: "+contract.label()+(blind?" · pe nevăzute ×2":""),()->apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,contract,blind)));
        }else if(v.phase()==RentzMatch.Phase.PLAY){text(v.contract().label()+(v.blind()?" · ×2":""),21);text("Joacă · Tu",17);
            if(v.contract()==Contract.RENTZ){for(var suit:Card.Suit.values()){var row=new StringBuilder(suitText(suit)+"  ");v.board().stream().filter(c->c.suit()==suit).sorted(java.util.Comparator.comparingInt(Card::rank)).forEach(c->row.append(c.label()).append(' '));text(row.toString(),17);}}
            else{var row=new StringBuilder("Levată: ");for(var play:v.trick())row.append(play.seat()==0?"Tu":"AI "+play.seat()).append(' ').append(play.card().label()).append("  ");text(row.toString(),18);}
            showCards(v,true);if(v.legal().isEmpty())button("Pas",()->apply(new GameSession.Command(GameSession.Action.PASS,0,null,null,false)));
        }else{text(v.phase()==RentzMatch.Phase.MATCH_END?"Partidă încheiată":"Mână încheiată",23);text("Punctele mâinii: "+v.dealPoints(),18);if(v.phase()==RentzMatch.Phase.DEAL_END)button("Mâna următoare",()->apply(new GameSession.Command(GameSession.Action.NEXT_HAND,0,null,null,false)));}
        button("Istoricul scorului",this::history);button("Meniu principal",this::menu);publish();
    }
    private void showCards(RentzMatch.Observation v,boolean playable){text("Cărțile tale",16);var grid=new GridLayout(this);grid.setColumnCount(4);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);page.addView(grid);
        for(var card:v.hand()){var b=new Button(this);b.setText(card.label());b.setTextSize(21);b.setAllCaps(false);b.setContentDescription("Joacă "+card.label());boolean legal=playable&&v.legal().contains(card);b.setEnabled(legal);b.setTextColor(card.suit()==Card.Suit.HEARTS||card.suit()==Card.Suit.DIAMONDS?Color.rgb(167,34,50):Color.rgb(30,37,34));var shape=new GradientDrawable();shape.setColor(legal?CREAM:Color.rgb(173,190,179));shape.setCornerRadius(dp(9));shape.setStroke(dp(1),legal?GOLD:Color.GRAY);b.setBackground(shape);var p=new GridLayout.LayoutParams();p.width=0;p.height=dp(72);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(3),dp(3),dp(3),dp(3));grid.addView(b,p);b.setOnClickListener(w->apply(new GameSession.Command(GameSession.Action.PLAY,0,card,null,false)));}
    }
    private void history(){var session=controller.session();screen="HISTORY";base("Istoricul scorului");var scroll=new HorizontalScrollView(this);var table=new TableLayout(this);scroll.addView(table);page.addView(scroll);var header=new TableRow(this);cell(header,"Mână · sub-joc");for(int seat=0;seat<session.view().players();seat++)cell(header,seat==0?"Tu · puncte / total":"AI "+seat+" · puncte / total");table.addView(header);
        for(var hand:session.history()){var row=new TableRow(this);cell(row,hand.number()+" · "+hand.contract().label()+(hand.blind()?" ×2":""));for(int seat=0;seat<hand.points().size();seat++)cell(row,hand.points().get(seat)+" / "+hand.totals().get(seat));table.addView(row);}button("Înapoi la joc",this::render);publish();}
    private void cell(TableRow row,String value){var t=new TextView(this);t.setText(value);t.setTextColor(CREAM);t.setPadding(dp(10),dp(10),dp(10),dp(10));row.addView(t);}
    private String suitText(Card.Suit suit){return switch(suit){case CLUBS->"♣";case DIAMONDS->"♦";case HEARTS->"♥";case SPADES->"♠";};}
    private void publish(){if(BuildConfig.DEBUG)try{controller.publish(screen);}catch(Exception error){android.util.Log.e("RentzLab","Debug actor projection failed",error);}}
    @Override public void onBackPressed(){if(!controller.isBusy())menu();}
    @Override protected void onResume(){super.onResume();try{if(controller.activate()){if(screen.equals("GAME"))act(controller::resume);else if(screen.equals("HISTORY"))history();else if(screen.equals("HELP"))help();else menu();}}catch(Exception error){showFailure(error);}}
    @Override protected void onPause(){controller.deactivate();super.onPause();}
    @Override protected void onStop(){controller.deactivate();super.onStop();}
    @Override protected void onDestroy(){controller.close();super.onDestroy();}
}
