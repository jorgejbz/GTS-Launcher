package com.jajua.gts;

/**
 * The dynamic effects and scenes the RGB controller supports, taken from the Magic Lantern resource
 * tables (RGB effect group, command ids 57-76; scene group, ids 1-28). Names are localised; the ids
 * are the controller's own, sent by {@link AmbientLights#setMode} and {@link AmbientLights#setScene}.
 */
final class Lights {
    private Lights() {
    }

    static final class Item {
        final int id;
        final String name;

        Item(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** RGB dynamic effects. "Destello" = flash variant, "Degradado" = fade variant. */
    static final Item[] EFFECTS = {
            new Item(57, "7 colores · destello"),
            new Item(58, "7 colores · degradado"),
            new Item(59, "R-G-B · destello"),
            new Item(60, "R-G-B · degradado"),
            new Item(61, "Y-C-P · destello"),
            new Item(62, "Y-C-P · degradado"),
            new Item(63, "Rojo · destello"),
            new Item(64, "Rojo · degradado"),
            new Item(65, "Verde · destello"),
            new Item(66, "Verde · degradado"),
            new Item(67, "Azul · destello"),
            new Item(68, "Azul · degradado"),
            new Item(69, "Amarillo · destello"),
            new Item(70, "Amarillo · degradado"),
            new Item(71, "Cian · destello"),
            new Item(72, "Cian · degradado"),
            new Item(73, "Morado · destello"),
            new Item(74, "Morado · degradado"),
            new Item(75, "Blanco · destello"),
            new Item(76, "Blanco · degradado"),
    };

    /** Built-in scenes. */
    static final Item[] SCENES = {
            new Item(1, "Amanecer"),
            new Item(2, "Atardecer"),
            new Item(3, "Cumpleaños"),
            new Item(4, "Vela"),
            new Item(5, "Fuegos artificiales"),
            new Item(6, "Fiesta"),
            new Item(7, "Cita"),
            new Item(8, "Cielo estrellado"),
            new Item(9, "Romántico"),
            new Item(10, "Disco"),
            new Item(11, "Arcoíris"),
            new Item(12, "Cine"),
            new Item(13, "Navidad"),
            new Item(14, "Fluido"),
            new Item(15, "Durmiendo"),
            new Item(16, "Océano"),
            new Item(17, "Bosque"),
            new Item(18, "Lectura"),
            new Item(19, "Trabajo"),
            new Item(20, "Deslumbrante"),
            new Item(21, "Suave"),
            new Item(22, "Boda"),
            new Item(23, "Nieve"),
            new Item(24, "Fuego"),
            new Item(25, "Relámpago"),
            new Item(26, "San Valentín"),
            new Item(27, "Halloween"),
            new Item(28, "Alerta"),
    };
}
