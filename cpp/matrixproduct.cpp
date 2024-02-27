#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <time.h>
#include <cstdlib>
#include <papi.h>

using namespace std;

#define SYSTEMTIME clock_t

void setupMatrixes(double **pha, double **phb, double **phc, int matrixSize) {
    int i,j;

    *pha = new double[matrixSize * matrixSize];
    *phb = new double[matrixSize * matrixSize];
    *phc = new double[matrixSize * matrixSize];

    for (i = 0; i < matrixSize; ++i)
        for (j = 0; j < matrixSize; ++j)
            (*pha)[i * matrixSize + j] = (double)1.0;

    for (i = 0; i < matrixSize; ++i)
        for (j = 0; j < matrixSize; ++j)
            (*phb)[i * matrixSize + j] = (double)(i+1);

    for (i = 0; i < matrixSize; ++i)
        for (j = 0; j < matrixSize; ++j)
            (*phc)[i * matrixSize + j] = (double)(i+1);
}

void deleteMatrixes(double *pha, double *phb, double *phc) {
    delete pha;
    delete phb;
    delete phc;
}

void OnMult(int matrixSize)
{

    SYSTEMTIME Time1, Time2;

    char st[100];
    int i, j, k;

    double *pha, *phb, *phc;

    // Setup Matrixes
    setupMatrixes(&pha, &phb, &phc, matrixSize);

    Time1 = clock();

    for (i = 0; i < matrixSize; ++i)
        for (j = 0; j < matrixSize; ++j)
            for (k = 0; k < matrixSize; ++k)
                phc[i * matrixSize + j] += pha[i * matrixSize + k] * phb[k * matrixSize + j];

    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    // display 10 elements of the result matrix to verify correctness
    cout << "Result matrix: " << endl;
    for (i = 0; i < 1; i++)
        for (j = 0; j < min(10, matrixSize); j++)
            cout << phc[j] << " ";
    cout << endl;

    // Free memory used by Matrixes
    deleteMatrixes(pha, phb, phc);
}

// add code here for line x line matriz multiplication
void OnMultLine(int matrixSize) {

    SYSTEMTIME Time1, Time2;

    char st[100];
    int i, j, k;

    double *pha, *phb, *phc;

    // Setup Matrixes
    setupMatrixes(&pha, &phb, &phc, matrixSize);

    Time1 = clock();

    for (i = 0; i < matrixSize; ++i)
        for (k = 0; k < matrixSize; ++k)
            for (j = 0; j < matrixSize; ++j)
                phc[i * matrixSize + j] += pha[i * matrixSize + k] * phb[k * matrixSize + j];

    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    // display 10 elements of the result matrix to verify correctness
    cout << "Result matrix: " << endl;
    for (i = 0; i < 1; ++i)
        for (j = 0; j < min(10, matrixSize); ++j)
            cout << phc[j] << " ";
    cout << endl;

    // Free memory used by Matrixes
    deleteMatrixes(pha, phb, phc);
}

// add code here for block x block matriz multiplication
void OnMultBlock(int matrixSize, int blockSize)
{
    SYSTEMTIME Time1, Time2;

    char st[100];
    int i, j, k, ii, jj, kk;

    double *pha, *phb, *phc;

    // Setup Matrixes
    setupMatrixes(&pha, &phb, &phc, matrixSize);

    Time1 = clock();

    //code for block x block matrix multiplication
    for(ii=0; ii<matrixSize; ii+=blockSize) {    
        for( kk=0; kk<matrixSize; kk+=blockSize){ 
            for( jj=0; jj<matrixSize; jj+=blockSize) {
                for (i = ii ; i < ii + blockSize ; i++) {    
                    for (k = kk ; k < kk + blockSize ; k++) {
                        for (j = jj ; j < jj + blockSize ; j++) {
                            phc[i*matrixSize+j] += pha[i*matrixSize+k] * phb[k*matrixSize+j];
                        }
                    }
                }
            }
        }
    }
    

    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    // display 10 elements of the result matrix to verify correctness
    cout << "Result matrix: " << endl;
    for (i = 0; i < 1; ++i)
        for (j = 0; j < min(10, matrixSize); ++j)
            cout << phc[j] << " ";

    cout << endl;

    // Free memory used by Matrixes
    deleteMatrixes(pha, phb, phc);

}

// function to run stats for the 3 types of multiplication
void runStats(int &EventSet, int &ret, long long values[]) {
    /*

    printf("------Regular Multiplication------\n\n");

	for (size_t n = 600; n <= 3000; n+=400) {	
		printf("n=%zu\n", n);
		// Start PAPI
        ret = PAPI_start(EventSet);
		if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
		OnMult(n);  
  		ret = PAPI_stop(EventSet, values);
  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
  		printf("L1 DCM: %lld \n",values[0]);
  		printf("L2 DCM: %lld \n",values[1]);
		printf("----\n");

		ret = PAPI_reset( EventSet );
		if ( ret != PAPI_OK )
			std::cout << "FAIL reset" << endl; 
	}

    printf("------Line Multiplication------\n\n");

	for (size_t n = 600; n <= 3000; n+=400) {	
		printf("n=%zu\n", n);
		// Start PAPI
		ret = PAPI_start(EventSet);
		if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
		OnMultLine(n);  
  		ret = PAPI_stop(EventSet, values);
  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
  		printf("L1 DCM: %lld \n",values[0]);
  		printf("L2 DCM: %lld \n",values[1]);
		printf("----\n");

		ret = PAPI_reset( EventSet );
		if ( ret != PAPI_OK )
			std::cout << "FAIL reset" << endl; 

	}

	for (size_t n = 4096; n <= 10240; n+=2048)  {	
		printf("n=%zu\n", n);
		// Start PAPI
		ret = PAPI_start(EventSet);
		if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
		OnMultLine(n);  
  		ret = PAPI_stop(EventSet, values);
  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
  		printf("L1 DCM: %lld \n",values[0]);
  		printf("L2 DCM: %lld \n",values[1]);
		printf("----\n");

		ret = PAPI_reset( EventSet );
		if ( ret != PAPI_OK )
			std::cout << "FAIL reset" << endl; 
	}
    */
    printf("------Block Multiplication------\n\n");

	for (size_t n = 4096; n <= 10240; n+=2048) {	
        for (size_t blockSize = 32; blockSize <= 1024; blockSize*=2) {
            printf("n=%zu\n", n);
            printf("blockSize=%zu\n", blockSize);
            // Start PAPI
            ret = PAPI_start(EventSet);
            if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
            OnMultBlock(n, blockSize);  
            ret = PAPI_stop(EventSet, values);
            if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
            printf("L1 DCM: %lld \n",values[0]);
            printf("L2 DCM: %lld \n",values[1]);
            printf("----\n");

            ret = PAPI_reset( EventSet );
            if ( ret != PAPI_OK )
                std::cout << "FAIL reset" << endl; 
        }
	}

}

void handle_error (int retval)
{
  printf("PAPI error %d: %s\n", retval, PAPI_strerror(retval));
  exit(1);
}

void init_papi() {
  int retval = PAPI_library_init(PAPI_VER_CURRENT);
  if (retval != PAPI_VER_CURRENT && retval < 0) {
    printf("PAPI library version mismatch!\n");
    exit(1);
  }
  if (retval < 0) handle_error(retval);

  std::cout << "PAPI Version Number: MAJOR: " << PAPI_VERSION_MAJOR(retval)
            << " MINOR: " << PAPI_VERSION_MINOR(retval)
            << " REVISION: " << PAPI_VERSION_REVISION(retval) << "\n";
}


int main (int argc, char *argv[])
{
	int matrixSize, blockSize;
	int op;
	
	int EventSet = PAPI_NULL;
  	long long values[2];
  	int ret;
	

	ret = PAPI_library_init( PAPI_VER_CURRENT );
	if ( ret != PAPI_VER_CURRENT )
		std::cout << "FAIL" << endl;


	ret = PAPI_create_eventset(&EventSet);
		if (ret != PAPI_OK) cout << "ERROR: create eventset" << endl;


	ret = PAPI_add_event(EventSet,PAPI_L1_DCM );
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L1_DCM" << endl;


	ret = PAPI_add_event(EventSet,PAPI_L2_DCM);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L2_DCM" << endl;


	op=1;
	do {
		cout << endl << "1. Multiplication" << endl;
        cout << "2. Line Multiplication" << endl;
        cout << "3. Block Multiplication" << endl;
        cout << "4. Run all stats" << endl;
        cout << "0. Quit" << endl;
        cout << "Selection?: ";
        cin >> op;
		
		if (op == 0) break;
        
        if (op != 4) {
            printf("Dimensions: lins=cols ? ");
            cin >> matrixSize;

            // Start counting
			ret = PAPI_start(EventSet);
			if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
        }

		switch (op){
			case 1: 
				OnMult(matrixSize);
				break;
			case 2:
				OnMultLine(matrixSize);  
				break;
			case 3:
				cout << "Block Size? ";
				cin >> blockSize;
				OnMultBlock(matrixSize, blockSize);  
				break;
            case 4:
                runStats(EventSet, ret, values);
                break;
        }

        if (op != 4) {
            ret = PAPI_stop(EventSet, values);
            if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
            printf("L1 DCM: %lld \n",values[0]);
            printf("L2 DCM: %lld \n",values[1]);

            ret = PAPI_reset(EventSet);
            if (ret != PAPI_OK)
                std::cout << "FAIL reset" << endl;
        }


	}while (op != 0);

	ret = PAPI_remove_event( EventSet, PAPI_L1_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_remove_event( EventSet, PAPI_L2_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_destroy_eventset( &EventSet );
	if ( ret != PAPI_OK )
		std::cout << "FAIL destroy" << endl;

}