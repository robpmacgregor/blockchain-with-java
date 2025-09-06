class ComputerFacadeTestDrive {
    public static void main(String[] args) {
        /* Your subsystems */
        Processor processor = new Processor();
        Monitor monitor = new Monitor();
        Keyboard keyboard = new Keyboard();

        ComputerFacade computerFacade = new ComputerFacade(processor, monitor, keyboard);

        computerFacade.turnOnComputer();
        computerFacade.turnOffComputer();
    }
}

class ComputerFacade {
    /* Your subsystems */
    Processor processor;
    Monitor monitor;
    Keyboard keyboard;

    public ComputerFacade(Processor processor, Monitor monitor, Keyboard keyboard) {
        this.processor = processor;
        this.monitor = monitor;
        this.keyboard = keyboard;
    }

    public void turnOnComputer() {
        processor.on();
        monitor.on();
        keyboard.on();
    }

    public void turnOffComputer() {
        keyboard.off();
        monitor.off();
        processor.off();

    }
}

class Processor {
    /* Your subsystem description */
    String subsystem = "Processor";

    public void on() {
        System.out.println(subsystem + " on");
    }

    public void off() {
        System.out.println(subsystem + " off");
    }
}

class Monitor {
    /* Your subsystem description */
    String subsystem = "Monitor";

    public void on() {
        System.out.println(subsystem + " on");
    }

    public void off() {
        System.out.println(subsystem + " off");
    }
}

class Keyboard {
    /* Your subsystem description */
    String subsystem = "Keyboard";

    public void on() {
        System.out.println(subsystem + " on");
        turnOnBacklight();
    }

    public void off() {
        System.out.println(subsystem + " off");
        turnOffBacklight();
    }

    private void turnOnBacklight() {
        System.out.println("Backlight is turned on");
    }

    private void turnOffBacklight() {
        System.out.println("Backlight is turned off");
    }
}