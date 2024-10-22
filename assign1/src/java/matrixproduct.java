import java.util.Scanner;

public class matrixproduct{
    public static void main(String[] args) {
        int op = 1, lin=0, col=0, blockSize = 0;
        Scanner sc = new Scanner(System.in);

        do{
            System.out.println();
            System.out.println("1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("3. Run all stats");
            System.out.printf("Selection?: ");
            op = sc.nextInt();

            if (op == 0 ) break;

            if(op != 3){
            System.out.printf("Dimensions: lins=cols ? ");
            lin = sc.nextInt();
            col = lin;
            }

            switch (op) {
                case 1:
                    OnMult(lin, col);
                    break;
                case 2:
				    OnMultLine(lin, col);
                    break;
                case 3:
                    runStats();  
                default:
                    break;
            }

        } while(op != 0);
    }

    public static void OnMult(int m_ar, int m_br){
        double temp;

        double pha[] = new double[m_ar * m_br];
        double phb[] = new double[m_ar * m_br];
        double phc[] = new double[m_ar * m_br];

        for(int i=0; i<m_ar; i++)
            for(int j=0; j<m_ar; j++)
                pha[i*m_ar + j] = (double)1.0;



        for(int i=0; i<m_br; i++)
            for(int j=0; j<m_br; j++)
                phb[i*m_br + j] = (double)(i+1);

        long time1 = System.nanoTime();

        for(int i = 0; i < m_ar; i++){
            for(int j = 0; j < m_br; j++){
                temp = 0;
                for( int k = 0; k < m_ar; k++){
                    temp += pha[i * m_ar + k] * phb[k * m_br +j];
                }
                phc[i * m_ar + j] = temp;
            }
        }
        long time2 = System.nanoTime();
        long duration = time2 - time1;

        double seconds = (double)duration / 1000000000.0;

        System.out.printf("Time: %3.3f seconds\n",seconds);


        System.out.println("Result matrix: ");
     
        for (int i = 0; i < 1; i++) {
            for (int j = 0; j < Math.min(10, m_br); j++) {
                System.out.print((int)phc[i*m_ar+j] + " ");
            }
        }
    }
    
    public static void OnMultLine(int m_ar, int m_br){
        long Time1, Time2;

	double temp;
	int i, j, k;

	double[] pha = new double[m_ar*m_ar];
	double[] phb = new double[m_ar*m_ar];
	double[] phc = new double[m_ar*m_ar];

		for(i=0; i<m_ar; i++)
			for(j=0; j<m_ar; j++)
				pha[i*m_ar + j] = (double)1.0;

		for(i=0; i<m_br; i++)
			for(j=0; j<m_br; j++)
				phb[i*m_br + j] = (double)(i+1);
		
		for(i=0; i<m_ar; i++)
			for(j=0; j<m_ar; j++)
				phc[i*m_ar + j] = (double)0;

		Time1 = System.currentTimeMillis();

		for(i=0; i<m_ar; i++)
		{
			for( k=0; k<m_ar; k++)
			{	
				for(j=0; j<m_br; j++)
				{
					phc[i*m_ar+j] += pha[i*m_ar+k] * phb[k*m_br+j];
				}
			}
		}

		Time2 = System.currentTimeMillis();

		System.out.printf("Time: %3.3f seconds\n", (double)(Time2-Time1)/1000);
		
		System.out.println("Result matrix: ");
		for (i = 0; i < 1; i++) {
			for (j = 0; j < Math.min(10, m_br); j++) {
				System.out.print(phc[j] + " ");
			}
		}
		System.out.println();
    }


    public static void runStats(){
        System.out.println("------Regular Multiplication------");

        for (int n = 600; n <= 3000; n+=400) {
            System.out.printf("n=%d\n", n);
            OnMult( n, n); 
            System.out.println("----\n");    
        }

        System.out.println("------Line Multiplication------");

        for (int n = 600; n <= 3000; n+=400) {
            System.out.printf("n=%d\n", n);
            OnMultLine( n, n); 
            System.out.println("----\n");    
        }

    }
}   
